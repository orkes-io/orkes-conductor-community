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
package io.orkes.conductor.client.e2e.util;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

public class Commons {
    public static String WORKFLOW_NAME = "test-sdk-java-workflow";
    public static String TASK_NAME = "test-sdk-java-task";
    public static String OWNER_EMAIL = "example@orkes.io";
    public static int WORKFLOW_VERSION = 1;

    public static TaskDef getTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(Commons.TASK_NAME);
        return taskDef;
    }

    public static StartWorkflowRequest getStartWorkflowRequest() {
        return new StartWorkflowRequest().withName(WORKFLOW_NAME).withVersion(WORKFLOW_VERSION);
    }
}
