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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.orkes.conductor.client.*;
import io.orkes.conductor.client.e2e.util.RegistrationUtil;
import io.orkes.conductor.client.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class SubWorkflowVersionTests extends AbstractConductorTest {
    @Test
    public void testSubWorkflowNullVersion() {

        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String parentWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String subWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        RegistrationUtil.registerWorkflowWithSubWorkflowDef(
                parentWorkflowName, subWorkflowName, taskName, metadataClient);
        WorkflowDef workflowDef = metadataClient.getWorkflowDef(parentWorkflowName, 1);
        // Set sub workflow version to null
        workflowDef.getTasks().get(0).getSubWorkflowParam().setVersion(null);
        metadataClient.updateWorkflowDefs(List.of(workflowDef), true);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWorkflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // User1 should be able to complete task/workflow
        String subWorkflowId =
                workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getSubWorkflowId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subWorkflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId(
                workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId());
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        await().atMost(42, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.COMPLETED.name());
                        });

        // Cleanup
        metadataClient.unregisterWorkflowDef(parentWorkflowName, 1);
        metadataClient.unregisterWorkflowDef(subWorkflowName, 1);
        metadataClient.unregisterTaskDef(taskName);
    }

    @Test
    public void testSubWorkflowEmptyVersion() {

        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String parentWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String subWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        RegistrationUtil.registerWorkflowWithSubWorkflowDef(
                parentWorkflowName, subWorkflowName, taskName, metadataClient);
        WorkflowDef workflowDef = metadataClient.getWorkflowDef(parentWorkflowName, 1);
        WorkflowDef subWorkflowDef = metadataClient.getWorkflowDef(subWorkflowName, null);
        subWorkflowDef.setVersion(1);
        metadataClient.updateWorkflowDefs(List.of(subWorkflowDef), true);
        subWorkflowDef.setVersion(2);
        metadataClient.updateWorkflowDefs(List.of(subWorkflowDef), true);
        // Set sub workflow version to empty in parent workflow definition
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        workflowDef.getTasks().get(0).setSubWorkflowParam(subWorkflowParams);
        metadataClient.updateWorkflowDefs(List.of(workflowDef), true);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWorkflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // User1 should be able to complete task/workflow
        String subWorkflowId =
                workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getSubWorkflowId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subWorkflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId(
                workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId());
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        // Check sub-workflow is executed with the latest version.
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.COMPLETED.name());
                            assertEquals(
                                    workflow1
                                            .getTasks()
                                            .get(0)
                                            .getWorkflowTask()
                                            .getSubWorkflowParam()
                                            .getVersion(),
                                    2);
                        });

        // Cleanup
        metadataClient.unregisterWorkflowDef(parentWorkflowName, 1);
        metadataClient.unregisterWorkflowDef(subWorkflowName, 1);
        metadataClient.unregisterTaskDef(taskName);
    }

    @Test
    public void testDynamicSubWorkflow() {
        String workflowName1 = "DynamicFanInOutTest_Version";
        String subWorkflowName = "test_subworkflow";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataClient);
        registerSubWorkflow(subWorkflowName, "test_task", metadataClient);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(0).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");
        workflowTask2.setType(TaskType.SUB_WORKFLOW.name());
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        workflowTask2.setSubWorkflowParam(subWorkflowParams);

        Map<String, Object> output = new HashMap<>();
        Map<String, Map<String, Object>> input = new HashMap<>();
        input.put("xdt1", Map.of("k1", "v1"));
        output.put("dynamicTasks", Arrays.asList(workflowTask2));
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
                            assertTrue(workflow1.getTasks().size() == 4);
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Workflow should be completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertTrue(workflow1.getTasks().size() == 4);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    WorkflowStatus.StatusEnum.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1
                                            .getTasks()
                                            .get(2)
                                            .getInputData()
                                            .get("subWorkflowVersion"),
                                    1l);
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
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
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test retry");
        workflowDef.setTasks(Arrays.asList(inline, dynamicFork, join));
        try {
            metadataClient1.registerWorkflowDef(workflowDef);
            metadataClient1.registerTaskDefs(Arrays.asList(taskDef, taskDef2, taskDef3, taskDef4));
        } catch (Exception e) {
        }
    }

    public static void registerSubWorkflow(
            String subWorkflowName, String taskName, MetadataClient metadataClient) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        taskDef.setRetryCount(0);

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName(taskName);
        inline.setName(taskName);
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.SIMPLE);
        inline.setInputParameters(Map.of("evaluatorType", "graaljs", "expression", "true;"));

        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName(subWorkflowName);
        subworkflowDef.setOwnerEmail("test@orkes.io");
        subworkflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        subworkflowDef.setDescription("Sub Workflow to test retry");
        subworkflowDef.setTasks(Arrays.asList(inline));
        subworkflowDef.setTimeoutSeconds(600);
        subworkflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerWorkflowDef(subworkflowDef);
    }
}
