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
package io.orkes.conductor.server.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;

import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "conductor.orkes.sweeper.enabled", havingValue = "true")
@Slf4j
public class OrkesWorkflowSweeper extends LifecycleAwareComponent {

    private final QueueDAO queueDAO;
    private final OrkesSweeperProperties sweeperProperties;
    private final OrkesWorkflowSweepWorker sweepWorker;

    public OrkesWorkflowSweeper(
            OrkesWorkflowSweepWorker sweepWorker,
            QueueDAO queueDAO,
            ConductorProperties properties,
            OrkesSweeperProperties sweeperProperties) {
        this.sweepWorker = sweepWorker;
        this.queueDAO = queueDAO;
        this.sweeperProperties = sweeperProperties;
        log.info("Initializing sweeper with {} threads", properties.getSweeperThreadCount());
    }

    // Reuse com.netflix.conductor.core.config.SchedulerConfiguration
    @Scheduled(
            fixedDelayString = "${conductor.orkes.sweeper.frequencyMillis:10}",
            initialDelayString = "${conductor.orkes.sweeper.frequencyMillis:10}")
    public void pollAndSweep() {
        try {
            if (!isRunning()) {
                log.trace("Component stopped, skip workflow sweep");
            } else {
                List<String> workflowIds =
                        queueDAO.pop(
                                DECIDER_QUEUE,
                                sweeperProperties.getSweepBatchSize(),
                                sweeperProperties.getQueuePopTimeout());
                if (workflowIds != null && workflowIds.size() > 0) {
                    // wait for all workflow ids to be "swept"
                    CompletableFuture.allOf(
                                    workflowIds.stream()
                                            .map(sweepWorker::sweepAsync)
                                            .toArray(CompletableFuture[]::new))
                            .get();
                    log.debug(
                            "Sweeper processed {} workflow from the decider queue, workflowIds: {}",
                            workflowIds.size(),
                            workflowIds);
                }
                // NOTE: Disabling the sweeper implicitly disables this metric.
                recordQueueDepth();
            }
        } catch (Exception e) {
            Monitors.error(OrkesWorkflowSweeper.class.getSimpleName(), "poll");
            log.error("Error when polling for workflows", e);
            if (e instanceof InterruptedException) {
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            }
        }
    }

    private void recordQueueDepth() {
        int currentQueueSize = queueDAO.getSize(DECIDER_QUEUE);
        Monitors.recordGauge(DECIDER_QUEUE, currentQueueSize);
    }
}
