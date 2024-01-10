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
package io.orkes.conductor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.tasks.http.HttpTask;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(
        basePackages = {"com.netflix.conductor", "io.orkes.conductor"},
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            ForkJoinDynamicTaskMapper.class,
                            HttpTask.class,
                            SubWorkflow.class
                        }))
@RequiredArgsConstructor
public class OrkesConductorApplication {

    @Autowired private MetadataDAO metadataDAO;

    public static void main(String[] args) throws IOException {
        System.setProperty("spring.devtools.restart.enabled", "false");
        loadExternalConfig();

        log.info("Completed loading external configuration");
        System.setProperty("es.set.netty.runtime.available.processors", "false");

        SpringApplication.run(OrkesConductorApplication.class, args);
    }

    @Bean
    public OpenApiCustomiser openApiCustomiser(Environment environment) {
        List<Server> servers = new ArrayList<>();
        Server server = new Server();
        server.setDescription("Conductor API Server");
        server.setUrl(environment.getProperty("conductor.swagger.url"));
        servers.add(server);
        return openApi ->
                openApi.servers(servers).getPaths().values().stream()
                        .flatMap(pathItem -> pathItem.readOperations().stream());
    }

    @Bean
    public OpenAPI openAPI() {
        log.info("OpenAPI Configuration....");
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Orkes Conductor API Server")
                                .description("Orkes Conductor API Server")
                                .version("v2"));
    }

    private static void loadExternalConfig() throws IOException {
        String configFile = System.getProperty("CONDUCTOR_CONFIG_FILE");
        if (configFile == null) {
            configFile = System.getenv("CONDUCTOR_CONFIG_FILE");
        }
        log.info("\nUsing {} as the configuration file", configFile);
        if (!StringUtils.isEmpty(configFile)) {
            FileSystemResource resource = new FileSystemResource(configFile);
            if (resource.exists()) {
                System.getenv()
                        .forEach(
                                (k, v) -> {
                                    log.info("System Env Props - Key: {}, Value: {}", k, v);
                                    if (k.startsWith("conductor")) {
                                        log.info(
                                                "\n\tSetting env property to system property: {}",
                                                k);
                                        System.setProperty(k, v);
                                    }
                                });
                Properties existingProperties = System.getProperties();
                existingProperties.forEach(
                        (k, v) -> log.info("Env Props - Key: {}, Value: {}", k, v));
                Properties properties = new Properties();
                properties.load(resource.getInputStream());
                properties.forEach(
                        (key, value) -> {
                            String keyString = (String) key;
                            if (existingProperties.getProperty(keyString) != null) {
                                log.info(
                                        "Property : {} already exists with value: {}",
                                        keyString,
                                        value);
                            } else {
                                log.info("Setting {} - {}", keyString, value);
                                System.setProperty(keyString, (String) value);
                            }
                        });
                log.info("Loaded {} properties from {}", properties.size(), configFile);
            } else {
                log.warn("Ignoring {} since it does not exist", configFile);
            }
        }
    }

    @PostConstruct
    public void loadSample() {
        try {

            log.info("Loading samples {}", metadataDAO);

            ObjectMapper om = new ObjectMapperProvider().getObjectMapper();
            InputStream tasksInputStream =
                    OrkesConductorApplication.class.getResourceAsStream("/tasks.json");
            InputStream workflowsInputStream =
                    OrkesConductorApplication.class.getResourceAsStream("/workflows.json");

            TypeReference<List<TaskDef>> tasks = new TypeReference<List<TaskDef>>() {};
            TypeReference<List<WorkflowDef>> workflows = new TypeReference<List<WorkflowDef>>() {};

            List<TaskDef> taskDefs = om.readValue(new InputStreamReader(tasksInputStream), tasks);
            List<WorkflowDef> workflowDefs =
                    om.readValue(new InputStreamReader(workflowsInputStream), workflows);

            taskDefs.forEach(taskDef -> metadataDAO.updateTaskDef(taskDef));
            workflowDefs.forEach(workflowDef -> metadataDAO.updateWorkflowDef(workflowDef));

        } catch (Exception e) {
            log.error("Error while loading sample workflows and tasks {}", e.getMessage(), e);
        }
    }
}
