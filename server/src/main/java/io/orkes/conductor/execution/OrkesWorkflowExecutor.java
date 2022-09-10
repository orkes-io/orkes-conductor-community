/*
 * Copyright 2022 Orkes, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.orkes.conductor.execution;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.*;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.service.ExecutionLockService;

import io.orkes.conductor.id.TimeBasedUUIDGenerator;
import io.orkes.conductor.metrics.MetricsCollector;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.model.TaskModel.Status.SCHEDULED;

@Component
@Slf4j
@Primary
public class OrkesWorkflowExecutor extends WorkflowExecutor {

    private static final LocalDateTime ORKES_EPOCH_TIME = LocalDateTime.of(2021, 1, 1, 0, 0);

    private final QueueDAO queueDAO;

    private final ExecutionDAOFacade orkesExecutionDAOFacade;
    private final SystemTaskRegistry systemTaskRegistry;
    private final ExecutorService taskUpdateExecutor;

    private final MetricsCollector metricsCollector;

    public OrkesWorkflowExecutor(
            DeciderService deciderService,
            MetadataDAO metadataDAO,
            QueueDAO queueDAO,
            MetadataMapperService metadataMapperService,
            WorkflowStatusListener workflowStatusListener,
            ExecutionDAOFacade executionDAOFacade,
            ConductorProperties properties,
            ExecutionLockService executionLockService,
            @Lazy SystemTaskRegistry systemTaskRegistry,
            ParametersUtils parametersUtils,
            IDGenerator idGenerator,
            MetricsCollector metricsCollector) {
        super(
                deciderService,
                metadataDAO,
                queueDAO,
                metadataMapperService,
                workflowStatusListener,
                executionDAOFacade,
                properties,
                executionLockService,
                systemTaskRegistry,
                parametersUtils,
                idGenerator);

        this.queueDAO = queueDAO;
        this.orkesExecutionDAOFacade = executionDAOFacade;
        this.systemTaskRegistry = systemTaskRegistry;
        this.metricsCollector = metricsCollector;

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

    public void updateTask(TaskResult taskResult) {
        log.info("Updating {}", taskResult);
        if (taskResult == null) {
            throw new RuntimeException("Task object is null");
        }

        log.trace("Update Task {} - {}", taskResult.getTaskId(), taskResult.getStatus());

        String workflowId = taskResult.getWorkflowInstanceId();

        TaskModel task =
                Optional.ofNullable(orkesExecutionDAOFacade.getTaskModel(taskResult.getTaskId()))
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "No such task found by id: "
                                                        + taskResult.getTaskId()));

        log.trace(
                "Task: {} belonging to Workflow {} being updated",
                task,
                task.getWorkflowInstanceId());

        String taskQueueName = QueueUtils.getQueueName(task);

        if (task.getStatus().isTerminal()) {
            // Task was already updated....
            queueDAO.remove(taskQueueName, taskResult.getTaskId());
            log.debug(
                    "Task: {} has already finished execution with status: {} within workflow: {}. Removed task from queue: {}",
                    task.getTaskId(),
                    task.getStatus(),
                    task.getWorkflowInstanceId(),
                    taskQueueName);
            Monitors.recordUpdateConflict(task.getTaskType(), "", task.getStatus());
            return;
        }

        // for system tasks, setting to SCHEDULED would mean restarting the task which is
        // undesirable
        // for worker tasks, set status to SCHEDULED and push to the queue
        if (!systemTaskRegistry.isSystemTask(task.getTaskType())
                && taskResult.getStatus() == TaskResult.Status.IN_PROGRESS) {
            task.setStatus(SCHEDULED);
        } else {
            task.setStatus(TaskModel.Status.valueOf(taskResult.getStatus().name()));
        }
        task.setOutputMessage(taskResult.getOutputMessage());
        task.setReasonForIncompletion(taskResult.getReasonForIncompletion());
        task.setWorkerId(taskResult.getWorkerId());
        task.setCallbackAfterSeconds(taskResult.getCallbackAfterSeconds());
        task.setOutputData(taskResult.getOutputData());
        task.setSubWorkflowId(taskResult.getSubWorkflowId());

        if (StringUtils.isNotBlank(taskResult.getExternalOutputPayloadStoragePath())) {
            task.setExternalOutputPayloadStoragePath(
                    taskResult.getExternalOutputPayloadStoragePath());
        }

        if (task.getStatus().isTerminal()) {
            task.setEndTime(System.currentTimeMillis());
        }

        // Update message in Task queue based on Task status
        switch (task.getStatus()) {
            case COMPLETED:
            case CANCELED:
            case FAILED:
            case FAILED_WITH_TERMINAL_ERROR:
            case TIMED_OUT:
                try {
                    queueDAO.remove(taskQueueName, taskResult.getTaskId());
                    log.debug(
                            "Task: {} removed from taskQueue: {} since the task status is {}",
                            task,
                            taskQueueName,
                            task.getStatus().name());
                } catch (Exception e) {
                    // Ignore exceptions on queue remove as it wouldn't impact task and workflow
                    // execution, and will be cleaned up eventually
                    String errorMsg =
                            String.format(
                                    "Error removing the message in queue for task: %s for workflow: %s",
                                    task.getTaskId(), workflowId);
                    log.warn(errorMsg, e);
                    Monitors.recordTaskQueueOpError(task.getTaskType(), "");
                }
                break;
            case IN_PROGRESS:
            case SCHEDULED:
                try {
                    long callBack = taskResult.getCallbackAfterSeconds();
                    queueDAO.postpone(
                            taskQueueName, task.getTaskId(), task.getWorkflowPriority(), callBack);
                    log.debug(
                            "Task: {} postponed in taskQueue: {} since the task status is {} with callbackAfterSeconds: {}",
                            task,
                            taskQueueName,
                            task.getStatus().name(),
                            callBack);
                } catch (Exception e) {
                    // Throw exceptions on queue postpone, this would impact task execution
                    String errorMsg =
                            String.format(
                                    "Error postponing the message in queue for task: %s for workflow: %s",
                                    task.getTaskId(), workflowId);
                    log.error(errorMsg, e);
                    Monitors.recordTaskQueueOpError(task.getTaskType(), "");
                    throw new RuntimeException(e);
                }
                break;
            default:
                break;
        }

        // Throw an ApplicationException if below operations fail to avoid workflow inconsistencies.
        try {
            orkesExecutionDAOFacade.updateTask(task);
        } catch (Exception e) {
            String errorMsg =
                    String.format(
                            "Error updating task: %s for workflow: %s",
                            task.getTaskId(), workflowId);
            log.error(errorMsg, e);
            Monitors.recordTaskUpdateError(task.getTaskType(), "");
            throw new RuntimeException(e);
        }

        taskResult.getLogs().forEach(taskExecLog -> taskExecLog.setTaskId(task.getTaskId()));
        orkesExecutionDAOFacade.addTaskExecLog(taskResult.getLogs());

        if (task.getStatus().isTerminal()) {
            long duration = getTaskDuration(0, task);
            long lastDuration = task.getEndTime() - task.getStartTime();
            Monitors.recordTaskExecutionTime(
                    task.getTaskDefName(), duration, true, task.getStatus());
            Monitors.recordTaskExecutionTime(
                    task.getTaskDefName(), lastDuration, false, task.getStatus());
        }

        try {
            taskUpdateExecutor.submit(() -> decide(workflowId));
        } catch (RejectedExecutionException ree) {
            metricsCollector.getCounter("task_update_deferred").increment();
            queueDAO.push(
                    Utils.DECIDER_QUEUE,
                    taskResult.getWorkflowInstanceId(),
                    getWorkflowFIFOPriority(
                            taskResult.getWorkflowInstanceId(), task.getWorkflowPriority()),
                    0);
        }
    }

    private long getTaskDuration(long s, TaskModel task) {
        long duration = task.getEndTime() - task.getStartTime();
        s += duration;
        if (task.getRetriedTaskId() == null) {
            return s;
        }
        return s
                + getTaskDuration(s, orkesExecutionDAOFacade.getTaskModel(task.getRetriedTaskId()));
    }

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
