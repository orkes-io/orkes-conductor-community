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
package com.netflix.conductor.redis.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;

@ConfigurationProperties("conductor.redis")
@Configuration
public class RedisProperties {

    private final ConductorProperties conductorProperties;

    @Autowired
    public RedisProperties(ConductorProperties conductorProperties) {
        this.conductorProperties = conductorProperties;
    }

    /**
     * Data center region. If hosting on Amazon the value is something like us-east-1, us-west-2
     * etc.
     */
    private String dataCenterRegion = "us-east-1";

    /**
     * Local rack / availability zone. For AWS deployments, the value is something like us-east-1a,
     * etc.
     */
    private String availabilityZone = "us-east-1c";

    /** The name of the redis / dynomite cluster */
    private String clusterName = "";

    /** Dynomite Cluster details. Format is host:port:rack separated by semicolon */
    private String hosts = null;

    /** The prefix used to prepend workflow data in redis */
    private String workflowNamespacePrefix = null;

    /** The prefix used to prepend keys for queues in redis */
    private String queueNamespacePrefix = null;

    /**
     * The domain name to be used in the key prefix for logical separation of workflow data and
     * queues in a shared redis setup
     */
    private String keyspaceDomain = null;

    /**
     * The maximum number of connections that can be managed by the connection pool on a given
     * instance
     */
    private int maxConnectionsPerHost = 10;

    /** Database number. Defaults to a 0. Can be anywhere from 0 to 15 */
    private int database = 0;

    /**
     * The maximum amount of time to wait for a connection to become available from the connection
     * pool
     */
    private Duration maxTimeoutWhenExhausted = Duration.ofMillis(800);

    /** The maximum retry attempts to use with this connection pool */
    private int maxRetryAttempts = 0;

    /** The read connection port to be used for connecting to dyno-queues */
    private int queuesNonQuorumPort = 22122;

    /** The time in seconds after which the in-memory task definitions cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    /** The time to live in seconds for which the event execution will be persisted */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration eventExecutionPersistenceTTL = Duration.ofSeconds(60);

    /** The time in seconds after which the in-memory metadata cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration metadataCacheRefreshInterval = Duration.ofSeconds(60);

    // Maximum number of idle connections to be maintained
    private int maxIdleConnections = 8;

    // Minimum number of idle connections to be maintained
    private int minIdleConnections = 5;

    private long minEvictableIdleTimeMillis = 1800000;

    private long timeBetweenEvictionRunsMillis = -1L;

    private boolean testWhileIdle = false;

    private int numTestsPerEvictionRun = 3;

    private boolean ssl;

    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    public int getMinIdleConnections() {
        return minIdleConnections;
    }

    public void setMinIdleConnections(int minIdleConnections) {
        this.minIdleConnections = minIdleConnections;
    }

    public int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    public void setMaxIdleConnections(int maxIdleConnections) {
        this.maxIdleConnections = maxIdleConnections;
    }

    public String getDataCenterRegion() {
        return dataCenterRegion;
    }

    public void setDataCenterRegion(String dataCenterRegion) {
        this.dataCenterRegion = dataCenterRegion;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public void setAvailabilityZone(String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public String getWorkflowNamespacePrefix() {
        return workflowNamespacePrefix;
    }

    public void setWorkflowNamespacePrefix(String workflowNamespacePrefix) {
        this.workflowNamespacePrefix = workflowNamespacePrefix;
    }

    public String getQueueNamespacePrefix() {
        return queueNamespacePrefix;
    }

    public void setQueueNamespacePrefix(String queueNamespacePrefix) {
        this.queueNamespacePrefix = queueNamespacePrefix;
    }

    public String getKeyspaceDomain() {
        return keyspaceDomain;
    }

    public void setKeyspaceDomain(String keyspaceDomain) {
        this.keyspaceDomain = keyspaceDomain;
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public Duration getMaxTimeoutWhenExhausted() {
        return maxTimeoutWhenExhausted;
    }

    public void setMaxTimeoutWhenExhausted(Duration maxTimeoutWhenExhausted) {
        this.maxTimeoutWhenExhausted = maxTimeoutWhenExhausted;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public int getQueuesNonQuorumPort() {
        return queuesNonQuorumPort;
    }

    public void setQueuesNonQuorumPort(int queuesNonQuorumPort) {
        this.queuesNonQuorumPort = queuesNonQuorumPort;
    }

    public Duration getTaskDefCacheRefreshInterval() {
        return taskDefCacheRefreshInterval;
    }

    public void setTaskDefCacheRefreshInterval(Duration taskDefCacheRefreshInterval) {
        this.taskDefCacheRefreshInterval = taskDefCacheRefreshInterval;
    }

    public Duration getEventExecutionPersistenceTTL() {
        return eventExecutionPersistenceTTL;
    }

    public void setEventExecutionPersistenceTTL(Duration eventExecutionPersistenceTTL) {
        this.eventExecutionPersistenceTTL = eventExecutionPersistenceTTL;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getQueuePrefix() {
        String prefix = getQueueNamespacePrefix() + "." + conductorProperties.getStack();
        if (getKeyspaceDomain() != null) {
            prefix = prefix + "." + getKeyspaceDomain();
        }
        return prefix;
    }

    public Duration getMetadataCacheRefreshInterval() {
        return metadataCacheRefreshInterval;
    }

    public void setMetadataCacheRefreshInterval(Duration metadataCacheRefreshInterval) {
        this.metadataCacheRefreshInterval = metadataCacheRefreshInterval;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }
}
