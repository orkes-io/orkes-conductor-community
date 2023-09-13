/*
 * Copyright 2023 Orkes, Inc.
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
package com.netflix.conductor.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.redis.dynoqueue.ConfigurationHostSupplier;
import com.netflix.dyno.connectionpool.Host;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "redis_standalone")
public class RedisStandaloneConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisStandaloneConfiguration.class);

    public JedisPool getJedisPool(RedisProperties redisProperties) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(redisProperties.getMaxConnectionsPerHost());
        log.info(
                "Starting conductor server using redis_standalone - use SSL? {}",
                redisProperties.isSsl());
        ConfigurationHostSupplier hostSupplier = new ConfigurationHostSupplier(redisProperties);
        Host host = hostSupplier.getHosts().get(0);

        if (host.getPassword() != null) {
            log.info("Connecting to Redis Standalone with AUTH");
            return new JedisPool(
                    config,
                    host.getHostName(),
                    host.getPort(),
                    Protocol.DEFAULT_TIMEOUT,
                    host.getPassword(),
                    redisProperties.getDatabase(),
                    redisProperties.isSsl());
        } else {
            return new JedisPool(
                    config,
                    host.getHostName(),
                    host.getPort(),
                    Protocol.DEFAULT_TIMEOUT,
                    null,
                    redisProperties.getDatabase(),
                    redisProperties.isSsl());
        }
    }
}
