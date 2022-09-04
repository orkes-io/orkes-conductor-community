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
package io.orkes.conductor.dao.indexer;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static io.orkes.conductor.dao.indexer.IndexValueExtractor.getIndexWords;
import static org.junit.jupiter.api.Assertions.*;

public class TestIndexValueExtractor {

    @Test
    public void testIndexer() {
        WorkflowModel model = new WorkflowModel();
        model.getTasks().add(new TaskModel());

        String uuid = UUID.randomUUID().toString();
        model.getInput().put("correlation_id", uuid);
        System.out.println("correlationId: " + uuid);
        model.getInput().put("keep", "abcd");

        String json =
                "{\n"
                        + "  \"response\": \"java.lang.Exception: I/O error on GET request for \\\"https://orkes-services.web.app2/data.json\\\": orkes-services.web.app2: nodename nor servname provided, or not known; nested exception is java.net.UnknownHostException: orkes-services.web.app2: nodename nor servname provided, or not known\"\n"
                        + "}";
        try {
            Map output = new ObjectMapperProvider().getObjectMapper().readValue(json, Map.class);
            model.getOutput().putAll(output);
        } catch (Exception e) {

        }

        for (int i = 0; i < 100; i++) {
            model.getTasks().get(0).getOutputData().put("id" + i, UUID.randomUUID().toString());
        }
        Collection<String> words = getIndexWords(model, 2, 50);

        // Sine all the UUIDs are longer than max word length, they should all get filtered out
        assertNotNull(words);
        assertEquals(2, words.size());
        assertTrue(words.contains("abcd"));
        assertTrue(words.contains(uuid), uuid + " not in the list of words : " + words);

        words = getIndexWords(model, 200, 50);
        System.out.println(words);
        System.out.println(words.size());
        words.stream().forEach(System.out::println);

        // All UUIDs shouldbe present
        assertNotNull(words);
        assertTrue(words.contains("https://orkes-services.web.app2/data.json"));
        assertTrue(words.contains(uuid));
    }
}
