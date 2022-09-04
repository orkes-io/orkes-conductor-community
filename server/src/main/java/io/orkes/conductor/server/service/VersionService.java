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
package io.orkes.conductor.server.service;

import java.io.IOException;
import java.util.Scanner;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class VersionService {

    @Value("${orkes.conductor.version.file:#{null}}")
    private Resource file;

    private String version = "N/A";

    @PostConstruct
    public void postConstruct() {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }

        try (Scanner sc = new Scanner(file.getInputStream())) {
            if (sc.hasNextLine()) {
                version = sc.nextLine();
            }
        } catch (IOException e) {
            log.warn("Could not load version file");
        }
    }

    public String getVersion() {
        return version;
    }
}
