/*
 * Copyright 2024 Orkes, Inc.
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import io.orkes.conductor.client.ApiClient;
import io.orkes.conductor.client.http.OrkesMetadataClient;
import io.orkes.conductor.client.http.OrkesTaskClient;
import io.orkes.conductor.client.http.OrkesWorkflowClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractConductorTest {

    protected static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    protected static GenericContainer conductor;

    protected static ApiClient apiClient;
    protected static io.orkes.conductor.client.WorkflowClient workflowClient;
    protected static io.orkes.conductor.client.TaskClient taskClient;
    protected static io.orkes.conductor.client.MetadataClient metadataClient;
    protected static WorkflowExecutor executor;

    protected static final String[] workflows =
            new String[] {
                "/metadata/rerun.json", "/metadata/popminmax.json", "/metadata/fail.json"
            };

    @SneakyThrows
    @BeforeAll
    public static final void setup() {
        String url = "http://localhost:8899/api";

        apiClient = new ApiClient(url);
        workflowClient = new OrkesWorkflowClient(apiClient);
        metadataClient = new OrkesMetadataClient(apiClient);
        taskClient = new OrkesTaskClient(apiClient);
        executor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 1000);

        for (String workflow : workflows) {
            InputStream resource = AbstractConductorTest.class.getResourceAsStream(workflow);
            WorkflowDef workflowDef =
                    objectMapper.readValue(new InputStreamReader(resource), WorkflowDef.class);
            metadataClient.updateWorkflowDefs(List.of(workflowDef), true);
        }
    }

    @AfterAll
    public static void cleanup() {
        executor.shutdown();
    }
}
