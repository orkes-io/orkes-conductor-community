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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.def.tasks.Switch;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import io.orkes.conductor.client.ApiClient;
import io.orkes.conductor.client.OrkesClients;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSDKTests extends AbstractConductorTest {

    @BeforeAll
    public static void init() {
        executor.initWorkers("io.orkes.conductor.client.e2e");
    }

    @Test
    public void testSDK() throws ExecutionException, InterruptedException, TimeoutException {
        ConductorWorkflow<Map<String, Object>> workflow = new ConductorWorkflow<>(executor);
        workflow.setName("sdk_integration_test");
        workflow.setVersion(1);
        workflow.setOwnerEmail("test@orkes.io");
        workflow.setVariables(new HashMap<>());
        workflow.add(new SimpleTask("task1", "task1").input("name", "orkes"));

        Switch decision = new Switch("decide_ref", "${workflow.input.caseValue}");
        decision.switchCase(
                "caseA", new SimpleTask("task1", "task1"), new SimpleTask("task1", "task11"));
        decision.switchCase("caseB", new SimpleTask("task2", "task2"));
        decision.defaultCase(new SimpleTask("task1", "default_task"));

        CompletableFuture<Workflow> future = workflow.executeDynamic(new HashMap<>());
        assertNotNull(future);
        Workflow run = future.get(20, TimeUnit.SECONDS);
        assertNotNull(run);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, run.getStatus());
        assertEquals(1, run.getTasks().size());
        assertEquals("Hello, orkes", run.getTasks().get(0).getOutputData().get("greetings"));
    }

    @AfterAll
    public static void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @WorkerTask("sum_numbers")
    public BigDecimal sum(BigDecimal num1, BigDecimal num2) {
        return num1.add(num2);
    }
}
