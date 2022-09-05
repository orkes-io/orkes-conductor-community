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
package com.netflix.conductor.core.execution.tasks;

import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.tasks.http.HttpTask;
import com.netflix.conductor.tasks.http.providers.RestTemplateProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HTTP;

@Slf4j
@Component(TASK_TYPE_HTTP)
public class HttpSync extends WorkflowSystemTask {

    private HttpTask httpTask;

    public HttpSync(RestTemplateProvider restTemplateProvider, ObjectMapper objectMapper) {
        super(TASK_TYPE_HTTP);
        httpTask = new HttpTask(restTemplateProvider, objectMapper);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        httpTask.execute(workflow, task, executor);
        return true;
    }

    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        try {
            httpTask.start(workflow, task, executor);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
