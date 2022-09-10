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
package io.orkes.conductor.execution.tasks;

import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

@Component(TASK_TYPE_SUB_WORKFLOW)
@Slf4j
public class SubWorkflowSync extends WorkflowSystemTask {

    private static final String SUB_WORKFLOW_ID = "subWorkflowId";

    private final SubWorkflow subWorkflow;
    private final ObjectMapper objectMapper;

    public SubWorkflowSync(ObjectMapper objectMapper) {
        super(TASK_TYPE_SUB_WORKFLOW);
        this.subWorkflow = new SubWorkflow(objectMapper);
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        subWorkflow.start(workflow, task, workflowExecutor);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        return subWorkflow.execute(workflow, task, workflowExecutor);
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        subWorkflow.cancel(workflow, task, workflowExecutor);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isAsyncComplete(TaskModel task) {
        return true;
    }

    @Override
    public String toString() {
        return subWorkflow.toString();
    }
}
