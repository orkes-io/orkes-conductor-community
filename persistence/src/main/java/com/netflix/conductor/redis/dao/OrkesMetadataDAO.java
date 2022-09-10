/*
 * Copyright 2022 Orkes, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.redis.dao;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.OrkesJedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Conditional(AnyRedisCondition.class)
public class OrkesMetadataDAO extends RedisMetadataDAO {

    private final ConcurrentMap<String, Long> missingTaskDefs = new ConcurrentHashMap<>();

    private final long taskDefCacheTTL;
    private final EventHandlerDAO eventHandlerDAO;
    private static final String WORKFLOW_DEF = "WORKFLOW_DEF";
    private static final String WORKFLOW_DEF_NAMES = "WORKFLOW_DEF_NAMES";
    private static final String WAIT_FOR_EVENT = "WAIT_FOR_EVENT";

    public OrkesMetadataDAO(
            OrkesJedisProxy orkesJedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties,
            EventHandlerDAO eventHandlerDAO) {
        super(orkesJedisProxy, objectMapper, conductorProperties, properties);
        taskDefCacheTTL = properties.getTaskDefCacheRefreshInterval().getSeconds() * 1000;
        log.info("taskDefCacheTTL set to {}", taskDefCacheTTL);
        this.eventHandlerDAO = eventHandlerDAO;
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        if (orkesJedisProxy.hexists(
                nsKey(WORKFLOW_DEF, def.getName()), String.valueOf(def.getVersion()))) {
            throw new ConflictException("Workflow with " + def.key() + " already exists!");
        }
        _createOrUpdate(def);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        _createOrUpdate(def);
    }

    private void _createOrUpdate(WorkflowDef workflowDef) {
        // First set the workflow def
        orkesJedisProxy.hset(
                nsKey(WORKFLOW_DEF, workflowDef.getName()),
                String.valueOf(workflowDef.getVersion()),
                toJson(workflowDef));

        orkesJedisProxy.sadd(nsKey(WORKFLOW_DEF_NAMES), workflowDef.getName());
        for (var task : workflowDef.getTasks()) {
            if (task.getType().equals(WAIT_FOR_EVENT)) {
                EventHandler eventHandler = new EventHandler();
                eventHandler.setEvent(task.getSink());
                eventHandler.setName(workflowDef.getName() + "_" + task.getTaskReferenceName());
                eventHandler.setActive(true);
                EventHandler.Action action = new EventHandler.Action();
                EventHandler.TaskDetails taskDetails = new EventHandler.TaskDetails();
                taskDetails.setTaskRefName(task.getTaskReferenceName());
                taskDetails.setWorkflowId("${targetWorkflowId}");
                taskDetails.setOutput(Map.of("orkes_wait_for_event_task", true));
                action.setComplete_task(taskDetails);
                action.setAction(EventHandler.Action.Type.complete_task);
                eventHandler.setActions(List.of(action));
                // TODO: extend EventHandlerDAO with exists and updateOrInsert
                if (eventHandlerDAO.getAllEventHandlers().stream()
                        .noneMatch(e -> e.getName().equals(eventHandler.getName()))) {
                    eventHandlerDAO.addEventHandler(eventHandler);
                } else {
                    eventHandlerDAO.updateEventHandler(eventHandler);
                }
            }
        }
        recordRedisDaoRequests("storeWorkflowDef", "n/a", workflowDef.getName());
    }

    public TaskDef getTaskDef(String name, boolean ignoreCache) {
        return ignoreCache ? super.getTaskDef(name) : getTaskDef(name);
    }

    @Override
    public TaskDef getTaskDef(String name) {
        Long lastChecked = missingTaskDefs.get(name);
        // If the last check is NOT null, ie the task was reported missing earlier
        // If so, check when was it last checked and if more than the configured TTL then refresh
        long now = System.currentTimeMillis();
        if (lastChecked != null && (now - lastChecked) < taskDefCacheTTL) {
            return null;
        }
        TaskDef found = super.getTaskDef(name);
        if (found == null) {
            missingTaskDefs.put(name, now);
        } else {
            missingTaskDefs.remove(name);
        }
        return found;
    }
}
