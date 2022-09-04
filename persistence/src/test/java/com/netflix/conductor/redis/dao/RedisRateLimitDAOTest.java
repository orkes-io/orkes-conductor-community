/*
 * Copyright 2021 Orkes, Inc.
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
package com.netflix.conductor.redis.dao;

import java.time.Duration;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.OrkesJedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.JedisPool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class RedisRateLimitDAOTest {

    private RedisRateLimitingDAO rateLimitingDao;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void init() {
        ConductorProperties conductorProperties = mock(ConductorProperties.class);
        RedisProperties properties = mock(RedisProperties.class);
        when(properties.getTaskDefCacheRefreshInterval()).thenReturn(Duration.ofSeconds(60));
        JedisPool jedisPool = mock(JedisPool.class);
        when(jedisPool.getResource()).thenReturn(new JedisMock());
        OrkesJedisProxy orkesJedisProxy = new OrkesJedisProxy(jedisPool);

        rateLimitingDao =
                new RedisRateLimitingDAO(
                        orkesJedisProxy, objectMapper, conductorProperties, properties);
    }

    @Test
    public void testExceedsRateLimitWhenNoRateLimitSet() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition");
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    public void testExceedsRateLimitWithinLimit() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition");
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setRateLimitPerFrequency(20);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    public void testExceedsRateLimitOutOfLimit() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition");
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setRateLimitPerFrequency(1);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }
}
