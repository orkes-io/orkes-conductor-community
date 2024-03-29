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
package com.netflix.conductor.redis.dynoqueue;

import java.util.Arrays;
import java.util.List;

import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;
import com.netflix.dyno.connectionpool.HostSupplier;

public class LocalhostHostSupplier implements HostSupplier {

    private final RedisProperties properties;

    public LocalhostHostSupplier(RedisProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Host> getHosts() {
        Host dynoHost =
                new HostBuilder()
                        .setHostname("localhost")
                        .setIpAddress("0")
                        .setRack(properties.getAvailabilityZone())
                        .setStatus(Host.Status.Up)
                        .createHost();
        return Arrays.asList(dynoHost);
    }
}
