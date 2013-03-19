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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.MutableMap;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Sets;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka broker instance.
 */
public class KafkaBrokerImpl extends SoftwareProcessImpl implements MessageBroker, KafkaBroker {
    private static final Logger log = LoggerFactory.getLogger(KafkaBrokerImpl.class);

    private static final AtomicLong brokers = new AtomicLong(0l);

    public KafkaBrokerImpl() {
        super();
    }
    public KafkaBrokerImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public KafkaBrokerImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public KafkaBrokerImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public void postConstruct() {
        setAttribute(BROKER_ID, brokers.incrementAndGet());
    }

    @Override
    public Integer getKafkaPort() { return getAttribute(KAFKA_PORT); }

    @Override
    public Long getBrokerId() { return getAttribute(BROKER_ID); }

    @Override
    public KafkaZookeeper getZookeeper() { return getConfig(ZOOKEEPER); }

    public KafkaTopic createTopic(Map properties) {
        KafkaTopic result = new KafkaTopic(properties, this);
        Entities.manage(result);
        result.create();
        return result;
    }

    @Override
    public Class getDriverInterface() {
        return KafkaBrokerDriver.class;
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = Sets.newLinkedHashSet(super.getRequiredOpenPorts());
        ports.add(getAttribute(KAFKA_PORT));
        log.debug("getRequiredOpenPorts detected expanded ports {} for {}", ports, this);
        return ports;
    }

    private volatile FunctionFeed functionFeed;
    private volatile JmxFeed jmxFeed;

    @Override
    protected void connectSensors() {
        String socketServerStatsMbean = "kafka:type=kafka.SocketServerStats";

        functionFeed = FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Object, Boolean>(SERVICE_UP)
                        .period(500, TimeUnit.MILLISECONDS)
                        .callable(new Callable<Boolean>() {
                            public Boolean call() throws Exception {
                                return getDriver().isRunning();
                            }
                        })
                        .onError(Functions.constant(Boolean.FALSE)))
                .build();

        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Long>(FETCH_REQUEST_COUNT)
                        .objectName(socketServerStatsMbean)
                        .attributeName("NumFetchRequests")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(TOTAL_FETCH_TIME)
                        .objectName(socketServerStatsMbean)
                        .attributeName("TotalFetchRequestMs")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Double>(MAX_FETCH_TIME)
                        .objectName(socketServerStatsMbean)
                        .attributeName("MaxFetchRequestMs")
                        .onError(Functions.constant(-1.0d)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PRODUCE_REQUEST_COUNT)
                        .objectName(socketServerStatsMbean)
                        .attributeName("NumProduceRequests")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(TOTAL_PRODUCE_TIME)
                        .objectName(socketServerStatsMbean)
                        .attributeName("TotalProduceRequestMs")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Double>(MAX_PRODUCE_TIME)
                        .objectName(socketServerStatsMbean)
                        .attributeName("MaxProduceRequestMs")
                        .onError(Functions.constant(-1.0d)))
                .pollAttribute(new JmxAttributePollConfig<Long>(BYTES_RECEIVED)
                        .objectName(socketServerStatsMbean)
                        .attributeName("TotalBytesRead")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(BYTES_SENT)
                        .objectName(socketServerStatsMbean)
                        .attributeName("TotalBytesWritten")
                        .onError(Functions.constant(-1l)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (functionFeed != null) functionFeed.stop();
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("kafkaPort", getKafkaPort());
    }

    @Override
    public void setBrokerUrl() {
        // TODO
    }

}
