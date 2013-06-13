/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.messaging.kafka;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.zookeeper.Zookeeper;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka broker instance.
 */
public class KafkaBrokerImpl extends SoftwareProcessImpl implements MessageBroker, KafkaBroker {

    private static final Logger log = LoggerFactory.getLogger(KafkaBrokerImpl.class);
    private static final ObjectName SOCKET_SERVER_STATS_MBEAN = JmxHelper.createObjectName("kafka:type=kafka.SocketServerStats");

    private volatile JmxFeed jmxFeed;

    public KafkaBrokerImpl() {
        super();
    }

    @Override
    public void init() {
        super.init();
        setAttribute(BROKER_ID, Math.abs(hashCode())); // Must be positive for partitioning to work
    }

    @Override
    public Integer getKafkaPort() { return getAttribute(KAFKA_PORT); }

    @Override
    public Integer getBrokerId() { return getAttribute(BROKER_ID); }

    @Override
    public Zookeeper getZookeeper() { return getConfig(ZOOKEEPER); }

    public KafkaTopic createTopic(Map<?, ?> properties) {
        KafkaTopic result = addChild(EntitySpecs.spec(KafkaTopic.class).configure(properties));
        Entities.manage(result);
        result.create();
        return result;
    }

    @Override
    public Class<?> getDriverInterface() {
        return KafkaBrokerDriver.class;
    }

    @Override
    public void waitForServiceUp(long duration, TimeUnit units) {
        super.waitForServiceUp(duration, units);

        // Wait for the MBean to exist
        JmxHelper helper = new JmxHelper(this);
        try {
            helper.assertMBeanExistsEventually(SOCKET_SERVER_STATS_MBEAN, units.toMillis(duration));
        } finally {
            helper.disconnect();
        }
    }

    @Override
    protected void connectSensors() {
        connectServiceUpIsRunning();

        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Long>(FETCH_REQUEST_COUNT)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("NumFetchRequests")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(TOTAL_FETCH_TIME)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("TotalFetchRequestMs")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Double>(MAX_FETCH_TIME)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("MaxFetchRequestMs")
                        .onError(Functions.constant(-1.0d)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PRODUCE_REQUEST_COUNT)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("NumProduceRequests")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(TOTAL_PRODUCE_TIME)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("TotalProduceRequestMs")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Double>(MAX_PRODUCE_TIME)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("MaxProduceRequestMs")
                        .onError(Functions.constant(-1.0d)))
                .pollAttribute(new JmxAttributePollConfig<Long>(BYTES_RECEIVED)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("TotalBytesRead")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(BYTES_SENT)
                        .objectName(SOCKET_SERVER_STATS_MBEAN)
                        .attributeName("TotalBytesWritten")
                        .onError(Functions.constant(-1l)))
                .build();

        setBrokerUrl();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("kafkaPort", getKafkaPort());
    }

    /** Use the {@link #getZookeeper() zookeeper} details if available, otherwise use our own host and port. */
    @Override
    public void setBrokerUrl() {
        Zookeeper zookeeper = getZookeeper();
        if (zookeeper != null) {
            setAttribute(BROKER_URL, String.format("zookeeper://%s:%d", zookeeper.getAttribute(HOSTNAME), zookeeper.getZookeeperPort()));
        } else {
            setAttribute(BROKER_URL, String.format("kafka://%s:%d", getAttribute(HOSTNAME), getKafkaPort()));
        }
    }

}
