/*
 * Copyright 2023 Orkes, Inc.
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
package io.orkes.conductor.server.service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

import io.orkes.conductor.metrics.MetricsCollector;

import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.core.config.SchedulerConfiguration.SWEEPER_EXECUTOR_NAME;
import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

@Component
@ConditionalOnProperty(name = "conductor.orkes.sweeper.enabled", havingValue = "true")
@Slf4j
public class OrkesWorkflowSweepWorker {

    private final QueueDAO queueDAO;
    private final ConductorProperties properties;
    private final WorkflowExecutor workflowExecutor;
    private final ExecutionDAO executionDAO;
    private final MetricsCollector metricsCollector;
    private final SystemTaskRegistry systemTaskRegistry;
    private final ExecutionLockService executionLockService;

    public OrkesWorkflowSweepWorker(
            QueueDAO queueDAO,
            WorkflowExecutor workflowExecutor,
            ExecutionDAO executionDAO,
            MetricsCollector metricsCollector,
            SystemTaskRegistry systemTaskRegistry,
            ExecutionLockService executionLockService,
            ConductorProperties properties) {
        this.queueDAO = queueDAO;
        this.executionDAO = executionDAO;
        this.metricsCollector = metricsCollector;
        this.systemTaskRegistry = systemTaskRegistry;
        this.executionLockService = executionLockService;
        this.properties = properties;
        this.workflowExecutor = workflowExecutor;
    }

    @Async(SWEEPER_EXECUTOR_NAME)
    public CompletableFuture<Void> sweepAsync(String workflowId) {
        metricsCollector.getTimer("workflowSweeper").record(() -> sweep(workflowId));
        return CompletableFuture.completedFuture(null);
    }

    private void sweep(String workflowId) {
        boolean workflowLocked = false;
        try {
            workflowLocked = executionLockService.acquireLock(workflowId);
            if (!workflowLocked) {
                return;
            }
            log.info("Running sweeper for workflow {}, acquired lock", workflowId);
            // 1. Run decide on the workflow
            WorkflowModel workflow = executionDAO.getWorkflow(workflowId, true);
            if (workflow == null) {
                log.warn(
                        "Workflow NOT found by id: {}. Removed it from decider queue safely.",
                        workflowId);
                queueDAO.remove(DECIDER_QUEUE, workflowId);
                return;
            }
            workflow = decideAndRemove(workflow);
            if (workflow == null || workflow.getStatus().isTerminal()) {
                log.debug(
                        "Repair/decide result for workflow {} - {}",
                        workflowId,
                        workflow == null ? null : workflow.getStatus());
                if (workflow == null) {
                    // The workflow does not exist anymore, possible if it was completed and
                    // archived
                    queueDAO.remove(DECIDER_QUEUE, workflowId);
                }
                return;
            }

            // 2. If decide returns false
            //    - Check if the workflow has at least one scheduled or in progress task?
            //    - If scheduled or in progress - Check if it exists in its corresponding queue, if
            // not add it back
            //    - If no scheduled or in progress task exists
            //         1. Set the last task as isExecuted = false to force a re-evaluation
            //         2. Call decide

            if (System.currentTimeMillis() - workflow.getUpdatedTime() < 60_000L) {
                // Only do this once every 60 second
                return;
            }
            List<TaskModel> pendingTasks = getAllPendingTasks(workflow);
            if (pendingTasks.size() > 0) {
                pendingTasks.forEach(this::ensurePendingTaskIsInQueue);
            } else {
                log.warn(
                        "Workflow {} doesn't have an open pending task, requires force evaluation",
                        workflow.getWorkflowId());
                forceSetLastTaskAsNotExecuted(workflow);
                // Decide again after setting isExecuted to false
                workflow = decideAndRemove(workflow);
                if (workflow == null || workflow.getStatus().isTerminal()) {
                    log.warn(
                            "Removing from decider after repair is done, {}, {}",
                            workflowId,
                            (workflow == null ? null : workflow.getStatus()));
                    queueDAO.remove(DECIDER_QUEUE, workflowId);
                    return;
                }
                log.debug(
                        "Force evaluation result for workflow {} - {}",
                        workflowId,
                        workflow.getStatus());
            }

            // 3. If parent workflow exists, call repair on that too - meaning ensure the parent is
            // in the decider queue
            if (workflow.getParentWorkflowId() != null) {
                ensureWorkflowExistsInDecider(workflow.getParentWorkflowId());
            }

            // 4. TODO: Don't do this now - Check the min timeout for all running tasks and set
            //          Math.min(minTime, 1 hour) for decider queue
            queueDAO.setUnackTimeout(
                    DECIDER_QUEUE, workflowId, properties.getWorkflowOffsetTimeout().toMillis());
        } catch (NotFoundException e) {
            log.warn("Workflow NOT found for id: {}. Removed it from decider queue", workflowId, e);
            queueDAO.remove(DECIDER_QUEUE, workflowId);
        } catch (Exception e) {
            log.error("Error running sweep for workflow {}", workflowId, e);
        } finally {
            if (workflowLocked) {
                executionLockService.releaseLock(workflowId);
                log.debug("Sweeper released lock for workflow {}", workflowId);
            }
        }
    }

    private void forceSetLastTaskAsNotExecuted(WorkflowModel workflow) {
        if (workflow.getTasks() != null && workflow.getTasks().size() > 0) {
            TaskModel taskModel = workflow.getTasks().get(workflow.getTasks().size() - 1);
            log.warn(
                    "Force setting isExecuted to false for last task - {} for workflow {}",
                    taskModel.getTaskId(),
                    taskModel.getWorkflowInstanceId());
            taskModel.setExecuted(false);
            executionDAO.updateWorkflow(workflow);
        }
    }

    private List<TaskModel> getAllPendingTasks(WorkflowModel workflow) {
        if (workflow.getTasks() != null && workflow.getTasks().size() > 0) {
            return workflow.getTasks().stream()
                    .filter(taskModel -> !taskModel.isExecuted())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /** Decide with lock and remove terminal workflow from <code>DECIDER_QUEUE</code> */
    private WorkflowModel decideAndRemove(WorkflowModel workflow) {
        WorkflowModel workflowModel = workflowExecutor.decide(workflow);
        if (workflowModel == null) {
            return null;
        }
        if (workflowModel.getStatus().isTerminal()) {
            queueDAO.remove(DECIDER_QUEUE, workflowModel.getWorkflowId());
        }
        return workflowModel;
    }

    private boolean ensurePendingTaskIsInQueue(TaskModel task) {
        if (shouldTaskExistInQueue(task)) {
            // Ensure QueueDAO contains this taskId
            String taskQueueName = QueueUtils.getQueueName(task);
            if (!queueDAO.containsMessage(taskQueueName, task.getTaskId())) {
                queueDAO.push(taskQueueName, task.getTaskId(), task.getCallbackAfterSeconds());
                log.info(
                        "Task {} in workflow {} re-queued for repairs",
                        task.getTaskId(),
                        task.getWorkflowInstanceId());
                metricsCollector
                        .getCounter("repairTaskReQueued", task.getTaskDefName())
                        .increment();
                return true;
            }
        }
        return false;
    }

    private boolean ensureWorkflowExistsInDecider(String workflowId) {
        if (StringUtils.isNotEmpty(workflowId)) {
            String queueName = Utils.DECIDER_QUEUE;
            if (!queueDAO.containsMessage(queueName, workflowId)) {
                queueDAO.push(
                        queueName, workflowId, properties.getWorkflowOffsetTimeout().getSeconds());
                log.info("Workflow {} re-queued for repairs", workflowId);
                Monitors.recordQueueMessageRepushFromRepairService(queueName);
                return true;
            }
        }
        return false;
    }

    private boolean shouldTaskExistInQueue(TaskModel task) {
        if (systemTaskRegistry.isSystemTask(task.getTaskType())) {
            WorkflowSystemTask workflowSystemTask = systemTaskRegistry.get(task.getTaskType());
            return workflowSystemTask.isAsync() // Is Async
                    // Not async complete OR is async complete, but in scheduled state
                    && (!workflowSystemTask.isAsyncComplete(task)
                            || (workflowSystemTask.isAsyncComplete(task)
                                    && task.getStatus() == TaskModel.Status.SCHEDULED))
                    // Status is IN_PROGRESS or SCHEDULED
                    && (task.getStatus() == TaskModel.Status.IN_PROGRESS
                            || task.getStatus() == TaskModel.Status.SCHEDULED);
        }
        return task.getStatus() == TaskModel.Status.SCHEDULED;
    }
}
