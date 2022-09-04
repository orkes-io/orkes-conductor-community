/*
 * Copyright 2020 Orkes, Inc.
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.netflix.conductor.redis.dynoqueue.LocalhostHostSupplier;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.JedisStandalone;
import com.netflix.conductor.redis.jedis.OrkesJedisProxy;
import com.netflix.dyno.connectionpool.HostSupplier;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.commands.JedisCommands;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "memory")
public class InMemoryRedisConfiguration {

    public static final JedisMock jedisMock = new JedisMock();

    @Bean
    public HostSupplier hostSupplier(RedisProperties properties) {
        return new LocalhostHostSupplier(properties);
    }

    @Bean
    public JedisMock jedisMock() {
        return jedisMock;
    }

    @Bean
    public JedisCommands jedisCommands() {
        return new JedisStandalone(jedisPool());
    }

    @Bean
    public JedisPool jedisPool() {
        return new JedisPool() {
            @Override
            public Jedis getResource() {
                return jedisMock;
            }
        };
    }

    @Primary
    @Bean
    public OrkesJedisProxy OrkesJedisProxy() {
        System.out.println("OrkesJedisProxy created");
        return new OrkesJedisProxy(jedisPool());
    }
}
