/*
 * Copyright 2022 Orkes, Inc.
 * <p>
 * Licensed under the Orkes Community License (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * https://github.com/orkes-io/licenses/blob/main/community/LICENSE.txt
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.redis.dao.RedisExecutionDAO;
import com.netflix.conductor.service.ExecutionLockService;

import io.orkes.conductor.id.TimeBasedUUIDGenerator;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

@Component
@Slf4j
@Primary
public class OrkesWorkflowExecutor extends WorkflowExecutor {

    private static final LocalDateTime ORKES_EPOCH_TIME = LocalDateTime.of(2021, 1, 1, 0, 0);

    private final QueueDAO queueDAO;

    private final ExecutionDAOFacade orkesExecutionDAOFacade;
    private final SystemTaskRegistry systemTaskRegistry;
    private final ExecutorService taskUpdateExecutor;
    private final RedisExecutionDAO executionDAO;

    public OrkesWorkflowExecutor(
            DeciderService deciderService,
            MetadataDAO metadataDAO,
            QueueDAO queueDAO,
            MetadataMapperService metadataMapperService,
            WorkflowStatusListener workflowStatusListener,
            TaskStatusListener taskStatusListener,
            ExecutionDAOFacade executionDAOFacade,
            ConductorProperties properties,
            ExecutionLockService executionLockService,
            @Lazy SystemTaskRegistry systemTaskRegistry,
            ParametersUtils parametersUtils,
            IDGenerator idGenerator,
            RedisExecutionDAO executionDAO,
            ApplicationEventPublisher applicationEventPublisher) {
        super(
                deciderService,
                metadataDAO,
                queueDAO,
                metadataMapperService,
                workflowStatusListener,
                taskStatusListener,
                executionDAOFacade,
                properties,
                executionLockService,
                systemTaskRegistry,
                parametersUtils,
                idGenerator,
                applicationEventPublisher);

        this.queueDAO = queueDAO;
        this.orkesExecutionDAOFacade = executionDAOFacade;
        this.systemTaskRegistry = systemTaskRegistry;
        this.executionDAO = executionDAO;

        int threadPoolSize = Runtime.getRuntime().availableProcessors() * 10;
        this.taskUpdateExecutor =
                new ThreadPoolExecutor(
                        threadPoolSize,
                        threadPoolSize,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(threadPoolSize) {
                            @Override
                            public boolean offer(Runnable runnable) {
                                try {
                                    return super.offer(runnable, 100, TimeUnit.MILLISECONDS);
                                } catch (InterruptedException ie) {
                                    return false;
                                }
                            }
                        },
                        new ThreadFactoryBuilder().setNameFormat("task-update-thread-%d").build());

        log.info("OrkesWorkflowExecutor initialized");
    }

    @Override
    public void retry(String workflowId, boolean resumeSubworkflowTasks) {
        WorkflowModel workflowModel = orkesExecutionDAOFacade.getWorkflowModel(workflowId, true);
        executionDAO.restoreWorkflow(workflowModel);
        super.retry(workflowId, resumeSubworkflowTasks);
        queueDAO.setUnackTimeout(DECIDER_QUEUE, workflowId, 0);
    }

    @Override
    public String rerun(RerunWorkflowRequest request) {
        WorkflowModel workflowModel =
                orkesExecutionDAOFacade.getWorkflowModel(request.getReRunFromWorkflowId(), true);
        executionDAO.restoreWorkflow(workflowModel);
        return super.rerun(request);
    }

    @Override
    boolean scheduleTask(WorkflowModel workflow, List<TaskModel> tasks) {
        List<TaskModel> tasksToBeQueued;
        boolean startedSystemTasks = false;

        try {
            if (tasks == null || tasks.isEmpty()) {
                return false;
            }

            // Get the highest seq number
            int count = workflow.getTasks().stream().mapToInt(TaskModel::getSeq).max().orElse(0);

            for (TaskModel task : tasks) {
                if (task.getSeq() == 0) { // Set only if the seq was not set
                    task.setSeq(++count);
                }
            }

            // metric to track the distribution of number of tasks within a workflow
            Monitors.recordNumTasksInWorkflow(
                    workflow.getTasks().size() + tasks.size(),
                    workflow.getWorkflowName(),
                    String.valueOf(workflow.getWorkflowVersion()));

            // Save the tasks in the DAO
            orkesExecutionDAOFacade.createTasks(tasks);

            List<TaskModel> systemTasks =
                    tasks.stream()
                            .filter(task -> systemTaskRegistry.isSystemTask(task.getTaskType()))
                            .collect(Collectors.toList());

            tasksToBeQueued =
                    tasks.stream()
                            .filter(task -> !systemTaskRegistry.isSystemTask(task.getTaskType()))
                            .collect(Collectors.toList());

            // Traverse through all the system tasks, start the sync tasks, in case of async, queue
            // the tasks
            List<Future<TaskModel>> futureExecutions = new ArrayList<>();
            for (TaskModel task : systemTasks) {
                WorkflowSystemTask workflowSystemTask = systemTaskRegistry.get(task.getTaskType());
                if (workflowSystemTask == null) {
                    throw new NotFoundException(
                            "No system task found by name %s", task.getTaskType());
                }
                if (task.getStatus() != null
                        && !task.getStatus().isTerminal()
                        && task.getStartTime() == 0) {
                    task.setStartTime(System.currentTimeMillis());
                }
                if (!workflowSystemTask.isAsync()) {
                    Future<TaskModel> future =
                            taskUpdateExecutor.submit(
                                    () -> {
                                        workflowSystemTask.start(workflow, task, this);
                                        return task;
                                    });
                    futureExecutions.add(future);
                    startedSystemTasks = true;
                } else {
                    tasksToBeQueued.add(task);
                }
            }

            futureExecutions.forEach(
                    future -> {
                        try {
                            TaskModel task = future.get();
                            orkesExecutionDAOFacade.updateTask(task);
                        } catch (Exception e) {
                            throw new NonTransientException(e.getMessage(), e);
                        }
                    });

        } catch (Exception e) {
            List<String> taskIds =
                    tasks.stream().map(TaskModel::getTaskId).collect(Collectors.toList());
            String errorMsg =
                    String.format(
                            "Error scheduling tasks: %s, for workflow: %s",
                            taskIds, workflow.getWorkflowId());
            log.error(errorMsg, e);
            Monitors.error(OrkesWorkflowExecutor.class.getSimpleName(), "scheduleTask");
            throw new TerminateWorkflowException(errorMsg);
        }

        // On addTaskToQueue failures, ignore the exceptions and let WorkflowRepairService take care
        // of republishing the messages to the queue.
        try {
            addTaskToQueue(tasksToBeQueued);
        } catch (Exception e) {
            List<String> taskIds =
                    tasksToBeQueued.stream().map(TaskModel::getTaskId).collect(Collectors.toList());
            String errorMsg =
                    String.format(
                            "Error pushing tasks to the queue: %s, for workflow: %s",
                            taskIds, workflow.getWorkflowId());
            log.warn(errorMsg, e);
            Monitors.error(OrkesWorkflowExecutor.class.getSimpleName(), "scheduleTask");
        }
        return startedSystemTasks;
    }

    private void addTaskToQueue(final List<TaskModel> tasks) {
        for (TaskModel task : tasks) {
            addTaskToQueue(task);
        }
    }

    @Override
    public void addTaskToQueue(TaskModel task) {
        // put in queue
        String taskQueueName = QueueUtils.getQueueName(task);
        if (task.getCallbackAfterSeconds() > 0) {
            queueDAO.push(
                    taskQueueName,
                    task.getTaskId(),
                    task.getWorkflowPriority(),
                    task.getCallbackAfterSeconds());
        } else {
            // Tasks should be prioritized based on the start time of the workflow
            int priority =
                    getWorkflowFIFOPriority(
                            task.getWorkflowInstanceId(), task.getWorkflowPriority());
            queueDAO.push(taskQueueName, task.getTaskId(), priority, 0);
        }
        log.trace(
                "Added task {} with priority {} to queue {} with call back seconds {}",
                task,
                task.getWorkflowPriority(),
                taskQueueName,
                task.getCallbackAfterSeconds());
    }

    static int getWorkflowFIFOPriority(String workflowId, int priority) {
        if (priority != 0) {
            return priority;
        }
        long workflowCreationTime = TimeBasedUUIDGenerator.getDate(workflowId);
        LocalDateTime creationTime =
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(workflowCreationTime), ZoneId.systemDefault());
        long secondsFromOrkesEpoch = Duration.between(ORKES_EPOCH_TIME, creationTime).getSeconds();
        return Long.valueOf(secondsFromOrkesEpoch).intValue();
    }
}
