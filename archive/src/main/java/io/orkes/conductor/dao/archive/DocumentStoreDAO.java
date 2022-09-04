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

import com.netflix.conductor.model.WorkflowModel;

/**
 * Document store is used to store completed workflow JSON data.
 *
 * @see io.orkes.conductor.dao.postgres.archive.PostgresArchiveDAO
 */
public interface DocumentStoreDAO {

    void createOrUpdateWorkflow(WorkflowModel workflow);

    boolean removeWorkflow(String workflowId);

    WorkflowModel getWorkflow(String workflowId, boolean includeTasks);
}
