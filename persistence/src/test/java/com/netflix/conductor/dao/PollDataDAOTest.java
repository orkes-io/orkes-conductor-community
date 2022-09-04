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
package com.netflix.conductor.dao;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.PollData;

import static org.junit.Assert.*;

public abstract class PollDataDAOTest {

    protected abstract PollDataDAO getPollDataDAO();

    // TODO review this test
    @Ignore
    @Test
    public void testPollData() {
        getPollDataDAO().updateLastPollData("taskDef", null, "workerId1");
        PollData pollData = getPollDataDAO().getPollData("taskDef", null);
        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals(pollData.getQueueName(), "taskDef");
        assertNull(pollData.getDomain());
        assertEquals(pollData.getWorkerId(), "workerId1");

        getPollDataDAO().updateLastPollData("taskDef", "domain1", "workerId1");
        pollData = getPollDataDAO().getPollData("taskDef", "domain1");
        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals(pollData.getQueueName(), "taskDef");
        assertEquals(pollData.getDomain(), "domain1");
        assertEquals(pollData.getWorkerId(), "workerId1");

        List<PollData> pData = getPollDataDAO().getPollData("taskDef");
        assertEquals(pData.size(), 2);

        pollData = getPollDataDAO().getPollData("taskDef", "domain2");
        assertNull(pollData);
    }
}
