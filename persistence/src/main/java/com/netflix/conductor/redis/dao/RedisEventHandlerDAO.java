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
package com.netflix.conductor.redis.dao;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.OrkesJedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

@Component
@Conditional(AnyRedisCondition.class)
public class RedisEventHandlerDAO extends BaseDynoDAO implements EventHandlerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisEventHandlerDAO.class);

    private static final String EVENT_HANDLERS = "EVENT_HANDLERS";
    private static final String EVENT_HANDLERS_BY_EVENT = "EVENT_HANDLERS_BY_EVENT";

    public RedisEventHandlerDAO(
            OrkesJedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler.getName(), "Missing Name");
        if (getEventHandler(eventHandler.getName()) != null) {
            throw new ConflictException(
                    "EventHandler with name %s already exists!", eventHandler.getName());
        }
        index(eventHandler);
        orkesJedisProxy.hset(nsKey(EVENT_HANDLERS), eventHandler.getName(), toJson(eventHandler));
        recordRedisDaoRequests("addEventHandler");
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler.getName(), "Missing Name");
        EventHandler existing = getEventHandler(eventHandler.getName());
        if (existing == null) {
            throw new NotFoundException(
                    "EventHandler with name %s not found!", eventHandler.getName());
        }
        index(eventHandler);
        orkesJedisProxy.hset(nsKey(EVENT_HANDLERS), eventHandler.getName(), toJson(eventHandler));
        recordRedisDaoRequests("updateEventHandler");
    }

    @Override
    public void removeEventHandler(String name) {
        EventHandler existing = getEventHandler(name);
        if (existing == null) {
            throw new NotFoundException("EventHandler with name %s not found!", name);
        }
        orkesJedisProxy.hdel(nsKey(EVENT_HANDLERS), name);
        recordRedisDaoRequests("removeEventHandler");
        removeIndex(existing);
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        Map<String, String> all = orkesJedisProxy.hgetAll(nsKey(EVENT_HANDLERS));
        List<EventHandler> handlers = new LinkedList<>();
        all.forEach(
                (key, json) -> {
                    EventHandler eventHandler = readValue(json, EventHandler.class);
                    handlers.add(eventHandler);
                });
        recordRedisDaoRequests("getAllEventHandlers");
        return handlers;
    }

    private void index(EventHandler eventHandler) {
        String event = eventHandler.getEvent();
        String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
        orkesJedisProxy.sadd(key, eventHandler.getName());
    }

    private void removeIndex(EventHandler eventHandler) {
        String event = eventHandler.getEvent();
        String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
        orkesJedisProxy.srem(key, eventHandler.getName());
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
        Set<String> names = orkesJedisProxy.smembers(key);
        List<EventHandler> handlers = new LinkedList<>();
        for (String name : names) {
            try {
                EventHandler eventHandler = getEventHandler(name);
                recordRedisDaoEventRequests("getEventHandler", event);
                if (eventHandler.getEvent().equals(event)
                        && (!activeOnly || eventHandler.isActive())) {
                    handlers.add(eventHandler);
                }
            } catch (NotFoundException nfe) {
                LOGGER.info("No matching event handler found for event: {}", event);
                throw nfe;
            }
        }
        return handlers;
    }

    private EventHandler getEventHandler(String name) {
        EventHandler eventHandler = null;
        String json = orkesJedisProxy.hget(nsKey(EVENT_HANDLERS), name);
        if (json != null) {
            eventHandler = readValue(json, EventHandler.class);
        }
        return eventHandler;
    }
}
