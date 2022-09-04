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
package io.orkes.conductor.dao.archive;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.IndexDAO;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "conductor.archive.db.enabled", havingValue = "true")
@Primary
@Slf4j
public class ArchivedIndexDAO implements IndexDAO {

    private final ArchiveDAO archiveDAO;

    public ArchivedIndexDAO(ArchiveDAO archiveDAO) {
        this.archiveDAO = archiveDAO;
    }

    @Override
    public void setup() throws Exception {}

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {}

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(WorkflowSummary workflow) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void indexTask(TaskSummary task) {
        return;
    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(TaskSummary task) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort) {
        return archiveDAO.searchWorkflows(query, freeText, start, count);
    }

    @Override
    public SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort) {
        throw new UnsupportedOperationException("Task search is not supported in this environment");
    }

    @Override
    public void removeWorkflow(String workflowId) {
        archiveDAO.removeWorkflow(workflowId);
    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        archiveDAO.removeWorkflow(workflowId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {}

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(
            String workflowInstanceId, String[] keys, Object[] values) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String get(String workflowInstanceId, String key) {
        return null;
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> logs) {
        archiveDAO.addTaskExecutionLogs(logs);
    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        archiveDAO.addTaskExecutionLogs(logs);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        return archiveDAO.getTaskExecutionLogs(taskId);
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {}

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        return null;
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void addMessage(String queue, Message msg) {}

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<Message> getMessages(String queue) {
        return null;
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        throw new UnsupportedOperationException("You do not need to use this! :)");
    }

    public long getWorkflowCount(String query, String freeText) {
        return 0;
    }
}
