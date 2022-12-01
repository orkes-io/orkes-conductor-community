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
package com.netflix.conductor.redis.dao;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.OrkesJedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

@Component
@Conditional(AnyRedisCondition.class)
public class RedisExecutionDAO extends BaseDynoDAO
        implements ExecutionDAO, ConcurrentExecutionLimitDAO {

    public static final Logger LOGGER = LoggerFactory.getLogger(RedisExecutionDAO.class);

    // Keys Families
    private static final String TASK_LIMIT_BUCKET = "TASK_LIMIT_BUCKET";
    private static final String IN_PROGRESS_TASKS = "IN_PROGRESS_TASKS";
    private static final String TASKS_IN_PROGRESS_STATUS =
            "TASKS_IN_PROGRESS_STATUS"; // Tasks which are in IN_PROGRESS status.
    private static final String WORKFLOW_TO_TASKS = "WORKFLOW_TO_TASKS";
    private static final String SCHEDULED_TASKS = "SCHEDULED_TASKS";
    private static final String TASK = "TASK";
    private static final String WORKFLOW = "WORKFLOW";
    private static final String PENDING_WORKFLOWS = "PENDING_WORKFLOWS";
    private static final String WORKFLOW_DEF_TO_WORKFLOWS = "WORKFLOW_DEF_TO_WORKFLOWS";
    private static final String EVENT_EXECUTION = "EVENT_EXECUTION";
    private final int ttlEventExecutionSeconds;

    public RedisExecutionDAO(
            OrkesJedisProxy orkesJedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(orkesJedisProxy, objectMapper, conductorProperties, properties);

        ttlEventExecutionSeconds = (int) properties.getEventExecutionPersistenceTTL().getSeconds();
    }

    private static String dateStr(Long timeInMs) {
        Date date = new Date(timeInMs);
        return dateStr(date);
    }

    private static String dateStr(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        return format.format(date);
    }

    private static List<String> dateStrBetweenDates(Long startdatems, Long enddatems) {
        List<String> dates = new ArrayList<>();
        Calendar calendar = new GregorianCalendar();
        Date startdate = new Date(startdatems);
        Date enddate = new Date(enddatems);
        calendar.setTime(startdate);
        while (calendar.getTime().before(enddate) || calendar.getTime().equals(enddate)) {
            Date result = calendar.getTime();
            dates.add(dateStr(result));
            calendar.add(Calendar.DATE, 1);
        }
        return dates;
    }

    public void restoreWorkflow(WorkflowModel workflowModel) {
        if (!workflowModel.getStatus().isTerminal()) {
            throw new IllegalArgumentException(
                    "Workflow is in "
                            + workflowModel.getStatus()
                            + " status.  "
                            + "Only the workflow with terminal status can be restored");
        }

        boolean update = !workflowModel.getStatus().isTerminal(); // Update if not completed yet
        // Bottoms up, first create tasks
        restoreTasks(workflowModel.getWorkflowId(), workflowModel.getTasks());
        // Then create a workflow
        insertOrUpdateWorkflow(workflowModel, update);
    }

    private void restoreTasks(String workflowId, List<TaskModel> tasks) {
        String workflowToTaskKey = nsKey(WORKFLOW_TO_TASKS, workflowId);
        orkesJedisProxy.del(workflowToTaskKey);
        for (TaskModel task : tasks) {

            // Step 1: Add the task to the DB
            updateTask(task);

            // Step 2: Correlate the task to the workflow
            correlateTaskToWorkflowInDS(task.getTaskId(), workflowId);
        }
    }

    @Override
    public List<TaskModel> getPendingTasksByWorkflow(String taskName, String workflowId) {
        List<TaskModel> tasks = new LinkedList<>();

        List<TaskModel> pendingTasks = getPendingTasksForTaskType(taskName);
        pendingTasks.forEach(
                pendingTask -> {
                    if (pendingTask.getWorkflowInstanceId().equals(workflowId)) {
                        tasks.add(pendingTask);
                    }
                });

        return tasks;
    }

    @Override
    public List<TaskModel> getTasks(String taskDefName, String startKey, int count) {
        List<TaskModel> tasks = new LinkedList<>();

        List<TaskModel> pendingTasks = getPendingTasksForTaskType(taskDefName);
        boolean startKeyFound = startKey == null;
        int foundcount = 0;
        for (TaskModel pendingTask : pendingTasks) {
            if (!startKeyFound) {
                if (pendingTask.getTaskId().equals(startKey)) {
                    startKeyFound = true;
                    if (startKey != null) {
                        continue;
                    }
                }
            }
            if (startKeyFound && foundcount < count) {
                tasks.add(pendingTask);
                foundcount++;
            }
        }
        return tasks;
    }

    @Override
    public List<TaskModel> createTasks(List<TaskModel> tasks) {

        List<TaskModel> tasksCreated = new LinkedList<>();

        for (TaskModel task : tasks) {
            validate(task);
            String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();
            Long added =
                    orkesJedisProxy.hset(
                            nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()),
                            taskKey,
                            task.getTaskId());
            if (added < 1) {
                LOGGER.debug(
                        "Task already scheduled, skipping the run "
                                + task.getTaskId()
                                + ", ref="
                                + task.getReferenceTaskName()
                                + ", key="
                                + taskKey);
                continue;
            }

            if (task.getStatus() != null
                    && !task.getStatus().isTerminal()
                    && task.getScheduledTime() == 0) {
                task.setScheduledTime(System.currentTimeMillis());
            }

            correlateTaskToWorkflowInDS(task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.debug(
                    "Scheduled task added to WORKFLOW_TO_TASKS workflowId: {}, taskId: {}, taskType: {} during createTasks",
                    task.getWorkflowInstanceId(),
                    task.getTaskId(),
                    task.getTaskType());

            String inProgressTaskKey = nsKey(IN_PROGRESS_TASKS, task.getTaskDefName());
            orkesJedisProxy.sadd(inProgressTaskKey, task.getTaskId());
            LOGGER.debug(
                    "Scheduled task added to IN_PROGRESS_TASKS with inProgressTaskKey: {}, workflowId: {}, taskId: {}, taskType: {} during createTasks",
                    inProgressTaskKey,
                    task.getWorkflowInstanceId(),
                    task.getTaskId(),
                    task.getTaskType());

            updateTask(task);
            tasksCreated.add(task);
        }

        return tasksCreated;
    }

    @Override
    public void updateTask(TaskModel task) {
        Optional<TaskDef> taskDefinition = task.getTaskDefinition();

        if (taskDefinition.isPresent() && taskDefinition.get().concurrencyLimit() > 0) {

            if (task.getStatus() != null && task.getStatus().equals(TaskModel.Status.IN_PROGRESS)) {
                orkesJedisProxy.sadd(
                        nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
                LOGGER.debug(
                        "Workflow Task added to TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
                        nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName(), task.getTaskId()),
                        task.getWorkflowInstanceId(),
                        task.getTaskId(),
                        task.getTaskType(),
                        task.getStatus().name());
            } else {
                orkesJedisProxy.srem(
                        nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
                LOGGER.debug(
                        "Workflow Task removed from TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
                        nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName(), task.getTaskId()),
                        task.getWorkflowInstanceId(),
                        task.getTaskId(),
                        task.getTaskType(),
                        task.getStatus().name());
                String key = nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName());
                orkesJedisProxy.zrem(key, task.getTaskId());
                LOGGER.debug(
                        "Workflow Task removed from TASK_LIMIT_BUCKET with taskLimitBucketKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
                        key,
                        task.getWorkflowInstanceId(),
                        task.getTaskId(),
                        task.getTaskType(),
                        task.getStatus().name());
            }
        }

        String payload = toJson(task);
        recordRedisDaoPayloadSize(
                "updateTask",
                payload.length(),
                taskDefinition.map(TaskDef::getName).orElse("n/a"),
                task.getWorkflowType());

        orkesJedisProxy.set(nsKey(TASK, task.getTaskId()), payload);
        LOGGER.debug(
                "Workflow task payload saved to TASK with taskKey: {}, workflowId: {}, taskId: {}, taskType: {} during updateTask",
                nsKey(TASK, task.getTaskId()),
                task.getWorkflowInstanceId(),
                task.getTaskId(),
                task.getTaskType());
        if (task.getStatus() != null && task.getStatus().isTerminal()) {
            orkesJedisProxy.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
            LOGGER.debug(
                    "Workflow Task removed from TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
                    nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()),
                    task.getWorkflowInstanceId(),
                    task.getTaskId(),
                    task.getTaskType(),
                    task.getStatus().name());
        }

        Set<String> taskIds =
                orkesJedisProxy.smembers(nsKey(WORKFLOW_TO_TASKS, task.getWorkflowInstanceId()));
        if (!taskIds.contains(task.getTaskId())) {
            correlateTaskToWorkflowInDS(task.getTaskId(), task.getWorkflowInstanceId());
        }
    }

    @Override
    public boolean exceedsLimit(TaskModel task) {
        Optional<TaskDef> taskDefinition = task.getTaskDefinition();
        if (taskDefinition.isEmpty()) {
            return false;
        }
        int limit = taskDefinition.get().concurrencyLimit();
        if (limit <= 0) {
            return false;
        }

        long current = getInProgressTaskCount(task.getTaskDefName());
        if (current >= limit) {
            LOGGER.info(
                    "Task execution count limited. task - {}:{}, limit: {}, current: {}",
                    task.getTaskId(),
                    task.getTaskDefName(),
                    limit,
                    current);
            Monitors.recordTaskConcurrentExecutionLimited(task.getTaskDefName(), limit);
            return true;
        }

        String rateLimitKey = nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName());
        double score = System.currentTimeMillis();
        String taskId = task.getTaskId();
        orkesJedisProxy.zaddnx(rateLimitKey, score, taskId);

        Set<String> ids = orkesJedisProxy.zrangeByScore(rateLimitKey, 0, score + 1, limit);
        boolean rateLimited = !ids.contains(taskId);
        if (rateLimited) {
            LOGGER.info(
                    "Task execution count limited. task - {}:{}, limit: {}, current: {}",
                    task.getTaskId(),
                    task.getTaskDefName(),
                    limit,
                    current);
            String inProgressKey = nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName());
            // Cleanup any items that are still present in the rate limit bucket but not in progress
            // anymore!
            ids.stream()
                    .filter(id -> !orkesJedisProxy.sismember(inProgressKey, id))
                    .forEach(id2 -> orkesJedisProxy.zrem(rateLimitKey, id2));
            Monitors.recordTaskRateLimited(task.getTaskDefName(), limit);
        }
        return rateLimited;
    }

    private void removeTaskMappings(TaskModel task) {
        String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();

        orkesJedisProxy.hdel(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey);
        orkesJedisProxy.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
        orkesJedisProxy.srem(
                nsKey(WORKFLOW_TO_TASKS, task.getWorkflowInstanceId()), task.getTaskId());
        orkesJedisProxy.srem(
                nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
        orkesJedisProxy.zrem(nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName()), task.getTaskId());
    }

    private void removeTaskMappingsWithExpiry(TaskModel task) {
        String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();

        orkesJedisProxy.hdel(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey);
        orkesJedisProxy.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
        orkesJedisProxy.srem(
                nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
        orkesJedisProxy.zrem(nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName()), task.getTaskId());
    }

    @Override
    public boolean removeTask(String taskId) {
        TaskModel task = getTask(taskId);
        if (task == null) {
            LOGGER.warn("No such task found by id {}", taskId);
            return false;
        }
        removeTaskMappings(task);

        orkesJedisProxy.del(nsKey(TASK, task.getTaskId()));
        return true;
    }

    private boolean removeTaskWithExpiry(String taskId, int ttlSeconds) {
        TaskModel task = getTask(taskId);
        if (task == null) {
            LOGGER.warn("No such task found by id {}", taskId);
            return false;
        }
        removeTaskMappingsWithExpiry(task);

        orkesJedisProxy.expire(nsKey(TASK, task.getTaskId()), ttlSeconds);
        return true;
    }

    @Override
    public TaskModel getTask(String taskId) {
        Preconditions.checkNotNull(taskId, "taskId cannot be null");
        return Optional.ofNullable(orkesJedisProxy.get(nsKey(TASK, taskId)))
                .map(
                        json -> {
                            TaskModel task = readValue(json, TaskModel.class);
                            recordRedisDaoPayloadSize(
                                    "getTask",
                                    toJson(task).length(),
                                    task.getTaskType(),
                                    task.getWorkflowType());
                            return task;
                        })
                .orElse(null);
    }

    @Override
    public List<TaskModel> getTasks(List<String> taskIds) {
        return taskIds.stream()
                .map(taskId -> nsKey(TASK, taskId))
                .map(orkesJedisProxy::get)
                .filter(Objects::nonNull)
                .map(
                        jsonString -> {
                            TaskModel task = readValue(jsonString, TaskModel.class);
                            return task;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskModel> getTasksForWorkflow(String workflowId) {
        Preconditions.checkNotNull(workflowId, "workflowId cannot be null");
        Set<String> taskIds = orkesJedisProxy.smembers(nsKey(WORKFLOW_TO_TASKS, workflowId));
        return getTasks(new ArrayList<>(taskIds));
    }

    @Override
    public List<TaskModel> getPendingTasksForTaskType(String taskName) {
        Preconditions.checkNotNull(taskName, "task name cannot be null");
        Set<String> taskIds = orkesJedisProxy.smembers(nsKey(IN_PROGRESS_TASKS, taskName));
        return getTasks(new ArrayList<>(taskIds));
    }

    @Override
    public String createWorkflow(WorkflowModel workflow) {
        return insertOrUpdateWorkflow(workflow, false);
    }

    @Override
    public String updateWorkflow(WorkflowModel workflow) {
        return insertOrUpdateWorkflow(workflow, true);
    }

    @Override
    public boolean removeWorkflow(String workflowId) {
        WorkflowModel workflow = getWorkflow(workflowId, true);
        if (workflow != null) {
            // Remove from lists
            String key =
                    nsKey(
                            WORKFLOW_DEF_TO_WORKFLOWS,
                            workflow.getWorkflowName(),
                            dateStr(workflow.getCreateTime()));
            orkesJedisProxy.srem(key, workflowId);
            orkesJedisProxy.srem(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflowId);

            // Remove the object
            orkesJedisProxy.del(nsKey(WORKFLOW, workflowId));
            for (TaskModel task : workflow.getTasks()) {
                removeTask(task.getTaskId());
            }
            return true;
        }
        return false;
    }

    public boolean removeWorkflowWithExpiry(String workflowId, int ttlSeconds) {
        WorkflowModel workflow = getWorkflow(workflowId, true);
        if (workflow != null) {
            // Remove from lists
            String key =
                    nsKey(
                            WORKFLOW_DEF_TO_WORKFLOWS,
                            workflow.getWorkflowName(),
                            dateStr(workflow.getCreateTime()));
            orkesJedisProxy.srem(key, workflowId);
            orkesJedisProxy.srem(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflowId);

            // Remove the object
            orkesJedisProxy.expire(nsKey(WORKFLOW, workflowId), ttlSeconds);
            for (TaskModel task : workflow.getTasks()) {
                removeTaskWithExpiry(task.getTaskId(), ttlSeconds);
            }
            orkesJedisProxy.expire(nsKey(WORKFLOW_TO_TASKS, workflowId), ttlSeconds);

            return true;
        }
        return false;
    }

    @Override
    public void removeFromPendingWorkflow(String workflowType, String workflowId) {
        orkesJedisProxy.del(nsKey(SCHEDULED_TASKS, workflowId));
        orkesJedisProxy.srem(nsKey(PENDING_WORKFLOWS, workflowType), workflowId);
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId) {
        return getWorkflow(workflowId, true);
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId, boolean includeTasks) {
        String json = orkesJedisProxy.get(nsKey(WORKFLOW, workflowId));
        WorkflowModel workflow = null;

        if (json != null) {
            workflow = readValue(json, WorkflowModel.class);
            if (includeTasks) {
                List<TaskModel> tasks = getTasksForWorkflow(workflowId);
                tasks.sort(Comparator.comparingInt(TaskModel::getSeq));
                workflow.setTasks(tasks);
            }
        }
        return workflow;
    }

    /**
     * @param workflowName name of the workflow
     * @param version the workflow version
     * @return list of workflow ids that are in RUNNING state <em>returns workflows of all versions
     *     for the given workflow name</em>
     */
    @Override
    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
        List<String> workflowIds;
        Set<String> pendingWorkflows =
                orkesJedisProxy.smembers(nsKey(PENDING_WORKFLOWS, workflowName));
        workflowIds = new LinkedList<>(pendingWorkflows);
        return workflowIds;
    }

    /**
     * @param workflowName name of the workflow
     * @param version the workflow version
     * @return list of workflows that are in RUNNING state
     */
    @Override
    public List<WorkflowModel> getPendingWorkflowsByType(String workflowName, int version) {
        Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
        List<String> workflowIds = getRunningWorkflowIds(workflowName, version);
        return workflowIds.stream()
                .map(this::getWorkflow)
                .filter(workflow -> workflow.getWorkflowVersion() == version)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowModel> getWorkflowsByType(
            String workflowName, Long startTime, Long endTime) {
        Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
        Preconditions.checkNotNull(startTime, "startTime cannot be null");
        Preconditions.checkNotNull(endTime, "endTime cannot be null");

        List<WorkflowModel> workflows = new LinkedList<>();

        // Get all date strings between start and end
        List<String> dateStrs = dateStrBetweenDates(startTime, endTime);
        dateStrs.forEach(
                dateStr -> {
                    String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, workflowName, dateStr);
                    orkesJedisProxy
                            .smembers(key)
                            .forEach(
                                    workflowId -> {
                                        try {
                                            WorkflowModel workflow = getWorkflow(workflowId);
                                            if (workflow.getCreateTime() >= startTime
                                                    && workflow.getCreateTime() <= endTime) {
                                                workflows.add(workflow);
                                            }
                                        } catch (Exception e) {
                                            LOGGER.error(
                                                    "Failed to get workflow: {}", workflowId, e);
                                        }
                                    });
                });

        return workflows;
    }

    @Override
    public List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowName, String correlationId, boolean includeTasks) {
        throw new UnsupportedOperationException(
                "This method is not implemented in RedisExecutionDAO. Please use ExecutionDAOFacade instead.");
    }

    @Override
    public boolean canSearchAcrossWorkflows() {
        return false;
    }

    /**
     * Inserts a new workflow/ updates an existing workflow in the datastore. Additionally, if a
     * workflow is in terminal state, it is removed from the set of pending workflows.
     *
     * @param workflow the workflow instance
     * @param update flag to identify if update or create operation
     * @return the workflowId
     */
    private String insertOrUpdateWorkflow(WorkflowModel workflow, boolean update) {
        Preconditions.checkNotNull(workflow, "workflow object cannot be null");

        List<TaskModel> tasks = workflow.getTasks();
        workflow.setTasks(new LinkedList<>());

        String payload = toJson(workflow);
        // Store the workflow object
        orkesJedisProxy.set(nsKey(WORKFLOW, workflow.getWorkflowId()), payload);
        recordRedisDaoPayloadSize(
                "storeWorkflow", payload.length(), "n/a", workflow.getWorkflowName());
        if (!update) {
            // Add to list of workflows for a workflowdef
            String key =
                    nsKey(
                            WORKFLOW_DEF_TO_WORKFLOWS,
                            workflow.getWorkflowName(),
                            dateStr(workflow.getCreateTime()));
            orkesJedisProxy.sadd(key, workflow.getWorkflowId());
        }
        // Add or remove from the pending workflows
        if (workflow.getStatus().isTerminal()) {
            orkesJedisProxy.srem(
                    nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflow.getWorkflowId());
        } else {
            orkesJedisProxy.sadd(
                    nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflow.getWorkflowId());
        }

        workflow.setTasks(tasks);
        return workflow.getWorkflowId();
    }

    /**
     * Stores the correlation of a task to the workflow instance in the datastore
     *
     * @param taskId the taskId to be correlated
     * @param workflowInstanceId the workflowId to which the tasks belongs to
     */
    @VisibleForTesting
    void correlateTaskToWorkflowInDS(String taskId, String workflowInstanceId) {
        String workflowToTaskKey = nsKey(WORKFLOW_TO_TASKS, workflowInstanceId);
        orkesJedisProxy.sadd(workflowToTaskKey, taskId);
        LOGGER.debug(
                "Task mapped in WORKFLOW_TO_TASKS with workflowToTaskKey: {}, workflowId: {}, taskId: {}",
                workflowToTaskKey,
                workflowInstanceId,
                taskId);
    }

    public long getPendingWorkflowCount(String workflowName) {
        String key = nsKey(PENDING_WORKFLOWS, workflowName);
        return orkesJedisProxy.scard(key);
    }

    @Override
    public long getInProgressTaskCount(String taskDefName) {
        String inProgressKey = nsKey(TASKS_IN_PROGRESS_STATUS, taskDefName);
        return orkesJedisProxy.scard(inProgressKey);
    }

    @Override
    public boolean addEventExecution(EventExecution eventExecution) {
        try {
            String key =
                    nsKey(
                            EVENT_EXECUTION,
                            eventExecution.getName(),
                            eventExecution.getEvent(),
                            eventExecution.getMessageId());
            String json = objectMapper.writeValueAsString(eventExecution);
            recordRedisDaoEventRequests("addEventExecution", eventExecution.getEvent());
            recordRedisDaoPayloadSize(
                    "addEventExecution", json.length(), eventExecution.getEvent(), "n/a");
            boolean added = orkesJedisProxy.hsetnx(key, eventExecution.getId(), json) == 1L;

            if (ttlEventExecutionSeconds > 0) {
                orkesJedisProxy.expire(key, ttlEventExecutionSeconds);
            }

            return added;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to add event execution for " + eventExecution.getId(), e);
        }
    }

    @Override
    public void updateEventExecution(EventExecution eventExecution) {
        try {

            String key =
                    nsKey(
                            EVENT_EXECUTION,
                            eventExecution.getName(),
                            eventExecution.getEvent(),
                            eventExecution.getMessageId());
            String json = objectMapper.writeValueAsString(eventExecution);
            LOGGER.info("updating event execution {}", key);
            orkesJedisProxy.hset(key, eventExecution.getId(), json);
            recordRedisDaoEventRequests("updateEventExecution", eventExecution.getEvent());
            recordRedisDaoPayloadSize(
                    "updateEventExecution", json.length(), eventExecution.getEvent(), "n/a");
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to update event execution for " + eventExecution.getId(), e);
        }
    }

    @Override
    public void removeEventExecution(EventExecution eventExecution) {
        try {
            String key =
                    nsKey(
                            EVENT_EXECUTION,
                            eventExecution.getName(),
                            eventExecution.getEvent(),
                            eventExecution.getMessageId());
            LOGGER.info("removing event execution {}", key);
            orkesJedisProxy.hdel(key, eventExecution.getId());
            recordRedisDaoEventRequests("removeEventExecution", eventExecution.getEvent());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to remove event execution for " + eventExecution.getId(), e);
        }
    }

    public List<EventExecution> getEventExecutions(
            String eventHandlerName, String eventName, String messageId, int max) {
        try {
            String key = nsKey(EVENT_EXECUTION, eventHandlerName, eventName, messageId);
            LOGGER.info("getting event execution {}", key);
            List<EventExecution> executions = new LinkedList<>();
            for (int i = 0; i < max; i++) {
                String field = messageId + "_" + i;
                String value = orkesJedisProxy.hget(key, field);
                if (value == null) {
                    break;
                }
                recordRedisDaoEventRequests("getEventExecution", eventHandlerName);
                recordRedisDaoPayloadSize(
                        "getEventExecution", value.length(), eventHandlerName, "n/a");
                EventExecution eventExecution = objectMapper.readValue(value, EventExecution.class);
                executions.add(eventExecution);
            }
            return executions;

        } catch (Exception e) {
            throw new RuntimeException("Unable to get event execution for " + e);
        }
    }

    private void validate(TaskModel task) {
        try {
            Preconditions.checkNotNull(task, "task object cannot be null");
            Preconditions.checkNotNull(task.getTaskId(), "Task id cannot be null");
            Preconditions.checkNotNull(
                    task.getWorkflowInstanceId(), "Workflow instance id cannot be null");
            Preconditions.checkNotNull(
                    task.getReferenceTaskName(), "Task reference name cannot be null");
        } catch (NullPointerException npe) {
            throw new IllegalArgumentException(npe);
        }
    }
}
