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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.messaging.qpid.QpidBroker;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Sets;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka zookeeper instance.
 */
public class KafkaZookeeperImpl extends SoftwareProcessImpl implements KafkaZookeeper {
    private static final Logger log = LoggerFactory.getLogger(KafkaZookeeperImpl.class);

    public KafkaZookeeperImpl() {
        super();
    }
    public KafkaZookeeperImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public KafkaZookeeperImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public KafkaZookeeperImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public Integer getZookeeperPort() { return getAttribute(ZOOKEEPER_PORT); }

    @Override
    public String getHostname() { return getAttribute(HOSTNAME); }

    @Override
    public Class getDriverInterface() {
        return KafkaZookeeperDriver.class;
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = Sets.newLinkedHashSet(super.getRequiredOpenPorts());
        ports.add(getAttribute(ZOOKEEPER_PORT));
        log.debug("getRequiredOpenPorts detected expanded ports {} for {}", ports, this);
        return ports;
    }

    private volatile FunctionFeed functionFeed;
    private volatile JmxFeed jmxFeed;

    @Override
    protected void connectSensors() {
        String zookeeperMbean = "org.apache.ZooKeeperService:name0=StandaloneServer_port-1";

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
                .pollAttribute(new JmxAttributePollConfig<Long>(OUTSTANDING_REQUESTS)
                        .objectName(zookeeperMbean)
                        .attributeName("OutstandingRequests")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_RECEIVED)
                        .objectName(zookeeperMbean)
                        .attributeName("PacketsReceived")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_SENT)
                        .objectName(zookeeperMbean)
                        .attributeName("PacketsSent")
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
        return super.toStringHelper().add("zookeeperPort", getZookeeperPort());
    }

}
