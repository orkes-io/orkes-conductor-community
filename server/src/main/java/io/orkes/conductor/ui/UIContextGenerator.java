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
package io.orkes.conductor.ui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UIContextGenerator {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final boolean isSecurityEnabled;
    private final String contextFilePath;

    public UIContextGenerator(
            Environment environment,
            ObjectMapper objectMapper,
            @Value("${conductor.ui.context.file:/usr/share/nginx/html/context.js}")
                    String contextFilePath) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.isSecurityEnabled = false;
        this.contextFilePath = contextFilePath;
    }

    public boolean writeUIContextFile() throws IOException {
        log.info("Writing UI Context file: {}", contextFilePath);
        File contextFile = new File(contextFilePath);
        if (Files.notExists(Paths.get(contextFile.toURI()))) {
            log.info("UI Context File doesn't exist, skipping");
            return false;
        }

        try (FileWriter fileWriter = new FileWriter(contextFile)) {
            ObjectWriter jsonWriter = objectMapper.writerWithDefaultPrettyPrinter();
            fileWriter.write(
                    String.format(
                            "window.conductor = %s;\n\n", conductorFeaturesAsJson(jsonWriter)));
            fileWriter.write(
                    String.format(
                            "\nwindow.auth0Identifiers = %s;\n",
                            jsonWriter.writeValueAsString(
                                    Map.of(
                                            "clientId", "NOT_ENABLED",
                                            "domain", "NOT_ENABLED"))));

            fileWriter.flush();
        }

        return true;
    }

    private String conductorFeaturesAsJson(ObjectWriter jsonWriter) throws JsonProcessingException {
        return jsonWriter.writeValueAsString(
                Map.of(
                        "TASK_VISIBILITY",
                                environment.getProperty(
                                        "conductor.security.default.taskVisibility", "READ"),
                        "ACCESS_MANAGEMENT", isSecurityEnabled,
                        "CREATOR_ENABLE_CREATOR",
                                environment.getProperty(
                                        "conductor.creatorUi.featuresEnabled", "false"),
                        "CREATOR_ENABLE_REAFLOW_DIAGRAM",
                                environment.getProperty(
                                        "conductor.creatorUi.reaflowDiagramEnabled", "false")));
    }
}
