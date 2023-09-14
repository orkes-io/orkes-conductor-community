package io.orkes.conductor.client.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.ApiClient;
import io.orkes.conductor.client.http.OrkesMetadataClient;
import io.orkes.conductor.client.http.OrkesTaskClient;
import io.orkes.conductor.client.http.OrkesWorkflowClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
public abstract class AbstractConductorTest {

    protected static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    protected static GenericContainer conductor;

    protected static ApiClient apiClient;
    protected static io.orkes.conductor.client.WorkflowClient workflowClient;
    protected static io.orkes.conductor.client.TaskClient taskClient;
    protected static io.orkes.conductor.client.MetadataClient metadataClient;
    protected static WorkflowExecutor executor;

    protected static final String[] workflows = new String[]{"/metadata/rerun.json", "/metadata/popminmax.json", "/metadata/fail.json"};

    @SneakyThrows
    @BeforeAll
    public static final void setup() {
        conductor = new GenericContainer(DockerImageName.parse("s1"))
                .withExposedPorts(8080);
        conductor.start();
        int port = conductor.getFirstMappedPort();
        String url = "http://localhost:" + port + "/api";

        apiClient = new ApiClient(url);
        workflowClient = new OrkesWorkflowClient(apiClient);
        metadataClient = new OrkesMetadataClient(apiClient);
        taskClient = new OrkesTaskClient(apiClient);
        executor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 1000);

        for (String workflow : workflows) {
            InputStream resource = AbstractConductorTest.class.getResourceAsStream(workflow);
            WorkflowDef workflowDef = objectMapper.readValue(new InputStreamReader(resource), WorkflowDef.class);
            metadataClient.updateWorkflowDefs(List.of(workflowDef), true);
        }
    }

    @AfterAll
    public static void cleanup() {
        executor.shutdown();
        conductor.stop();
    }





}

