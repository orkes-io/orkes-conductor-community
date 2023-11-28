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
package io.orkes.conductor.client.e2e;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.orkes.conductor.client.*;
import io.orkes.conductor.client.http.OrkesMetadataClient;
import io.orkes.conductor.client.http.OrkesTaskClient;
import io.orkes.conductor.client.http.OrkesWorkflowClient;
import io.orkes.conductor.client.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class DynamicForkOptionalTests extends AbstractConductorTest {

    @Test
    public void testTaskDynamicForkOptional() {

        String workflowName1 = "DynamicFanInOutTest";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataClient);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.println("Started " + workflowId);

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(0).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        List<WorkflowTask> fanoutTasks = new ArrayList<>();
        Map<String, Map<String, Object>> input = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            WorkflowTask workflowTask = new WorkflowTask();
            workflowTask.setName("integration_task_2");
            workflowTask.setTaskReferenceName("xdt" + i);
            workflowTask.setOptional(true);
            fanoutTasks.add(workflowTask);

            input.put("xdt" + i, Map.of("k1", "v1"));

        }




        Map<String, Object> output = new HashMap<>();

        output.put("dynamicTasks", fanoutTasks);
        output.put("dynamicTasksInput", input);
        taskResult.setOutputData(output);
        taskClient.updateTask(taskResult);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.RUNNING.name());
                            assertTrue(workflow1.getTasks().size() == 5);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(3).getTaskId());
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Since the tasks are marked as optional. The workflow should be in running state.
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertTrue(workflow1.getTasks().size() == 6);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.RUNNING.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                            assertEquals(
                                    workflow1.getTasks().get(5).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        workflow = workflowClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(5).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Workflow should be completed
        await().atMost(100, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertTrue(workflow1.getTasks().size() == 6);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                        });

        metadataClient.unregisterWorkflowDef(workflowName1, 1);
    }


    @Test
    public void testLargeFork() {

        String workflowName1 = "DynamicFanInOutTest";
        int count = 200;

        // Register workflow
        registerWorkflowDef(workflowName1, metadataClient);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.println("Started " + workflowId);

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(0).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        List<WorkflowTask> fanoutTasks = new ArrayList<>();
        Map<String, Map<String, Object>> input = new HashMap<>();

        for (int i = 0; i < count; i++) {
            WorkflowTask workflowTask = new WorkflowTask();
            workflowTask.setName("integration_task_2");
            workflowTask.setTaskReferenceName("xdt" + i);
            workflowTask.setOptional(true);
            fanoutTasks.add(workflowTask);

            input.put("xdt" + i, Map.of("k1", "v1"));

        }

        Map<String, Object> output = new HashMap<>();

        output.put("dynamicTasks", fanoutTasks);
        output.put("dynamicTasksInput", input);
        taskResult.setOutputData(output);
        taskClient.updateTask(taskResult);

        workflow = workflowClient.getWorkflow(workflowId, true);
        for (Task task : workflow.getTasks()) {
            if(!task.getStatus().isTerminal()) {
                taskResult = new TaskResult();
                taskResult.setWorkflowInstanceId(workflowId);
                taskResult.setTaskId(task.getTaskId());
                taskResult.setStatus(TaskResult.Status.COMPLETED);
                taskClient.updateTask(taskResult);
            }
        }
        workflow = workflowClient.getWorkflow(workflowId, true);
        System.out.println("workflow status: " + workflow.getStatus());


    }

    @Test
    public void testTaskDynamicForkRetryCount() {

        String workflowName1 = "DynamicFanInOutTest1";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataClient);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);

        workflow = workflowClient.getWorkflow(workflowId, true);
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(0).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");
        workflowTask2.setOptional(true);
        workflowTask2.setSink("kitchen_sink");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("integration_task_3");
        workflowTask3.setTaskReferenceName("xdt2");
        workflowTask3.setRetryCount(2);

        Map<String, Object> output = new HashMap<>();
        Map<String, Map<String, Object>> input = new HashMap<>();
        input.put("xdt1", Map.of("k1", "v1"));
        input.put("xdt2", Map.of("k2", "v2"));
        output.put("dynamicTasks", Arrays.asList(workflowTask2, workflowTask3));
        output.put("dynamicTasksInput", input);
        taskResult.setOutputData(output);
        taskClient.updateTask(taskResult);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.RUNNING.name());
                            assertTrue(workflow1.getTasks().size() == 5);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getWorkflowTask().getSink(),
                                    "kitchen_sink");
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(3).getTaskId());
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Since the retry count is 2 task will be retried.
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.RUNNING.name());
                            assertTrue(workflow1.getTasks().size() == 6);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                            assertEquals(
                                    workflow1.getTasks().get(5).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        workflow = workflowClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(5).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Workflow should be completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.COMPLETED.name());
                            assertTrue(workflow1.getTasks().size() >= 6);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(5).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                        });

        metadataClient.unregisterWorkflowDef(workflowName1, 1);
    }

    private void registerWorkflowDef(String workflowName, MetadataClient metadataClient1) {
        TaskDef taskDef = new TaskDef("dt1");
        taskDef.setOwnerEmail("test@orkes.io");

        TaskDef taskDef4 = new TaskDef("integration_task_2");
        taskDef4.setOwnerEmail("test@orkes.io");

        TaskDef taskDef3 = new TaskDef("integration_task_3");
        taskDef3.setOwnerEmail("test@orkes.io");

        TaskDef taskDef2 = new TaskDef("dt2");
        taskDef2.setOwnerEmail("test@orkes.io");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("dt2");
        workflowTask.setName("dt2");
        workflowTask.setTaskDefinition(taskDef2);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName("dt1");
        inline.setName("dt1");
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask join = new WorkflowTask();
        join.setTaskReferenceName("join_dynamic");
        join.setName("join_dynamic");
        join.setWorkflowTaskType(TaskType.JOIN);

        WorkflowTask dynamicFork = new WorkflowTask();
        dynamicFork.setTaskReferenceName("dynamicFork");
        dynamicFork.setName("dynamicFork");
        dynamicFork.setTaskDefinition(taskDef);
        dynamicFork.setWorkflowTaskType(TaskType.FORK_JOIN_DYNAMIC);
        dynamicFork.setInputParameters(
                Map.of(
                        "dynamicTasks",
                        "${dt1.output.dynamicTasks}",
                        "dynamicTasksInput",
                        "${dt1.output.dynamicTasksInput}"));
        dynamicFork.setDynamicForkTasksParam("dynamicTasks");
        dynamicFork.setDynamicForkTasksInputParamName("dynamicTasksInput");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test retry");
        workflowDef.setTasks(Arrays.asList(inline, dynamicFork, join));
        try {
            metadataClient1.registerWorkflowDef(workflowDef);
            metadataClient1.registerTaskDefs(Arrays.asList(taskDef, taskDef2, taskDef3, taskDef4));
        } catch (Exception e) {
        }
    }
}
