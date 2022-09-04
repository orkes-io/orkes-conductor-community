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
package io.orkes.conductor.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.orkes.conductor.server.service.VersionService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@RestController
@RequiredArgsConstructor
public class VersionResource {

    private final VersionService versionService;

    @GetMapping(value = "/api/version", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Get the server's version")
    public String getVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version == null ? "n/a" : version;
    }
}
