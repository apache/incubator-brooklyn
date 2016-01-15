/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.entity.messaging.activemq;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.messaging.jms.JMSBrokerImpl;
import org.apache.brooklyn.feed.jmx.JmxAttributePollConfig;
import org.apache.brooklyn.feed.jmx.JmxFeed;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBrokerImpl extends JMSBrokerImpl<ActiveMQQueue, ActiveMQTopic> implements ActiveMQBroker {
    private static final Logger log = LoggerFactory.getLogger(ActiveMQBrokerImpl.class);

    private volatile JmxFeed jmxFeed;

    public ActiveMQBrokerImpl() {
        super();
    }

    @Override
    public void init() {
        super.init();
        Entities.getRequiredUrlConfig(this, TEMPLATE_CONFIGURATION_URL);
    }
    
    public void setBrokerUrl() {
        sensors().set(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)));
    }
    
    public Integer getJmxPort() {
        return !isJmxEnabled() ? Integer.valueOf(-1) : getAttribute(UsesJmx.JMX_PORT);
    }
    
    public String getBrokerName() {
        return getAttribute(BROKER_NAME);
    }
    
    public Integer getOpenWirePort() {
        return getAttribute(OPEN_WIRE_PORT);
    }
    
    public boolean isJmxEnabled() {
        return Boolean.TRUE.equals(getConfig(USE_JMX));
    }

    @Override
    public ActiveMQQueue createQueue(Map properties) {
        ActiveMQQueue result = addChild(EntitySpec.create(ActiveMQQueue.class).configure(properties));
        result.create();
        return result;
    }

    @Override
    public ActiveMQTopic createTopic(Map properties) {
        ActiveMQTopic result = addChild(EntitySpec.create(ActiveMQTopic.class).configure(properties));
        result.create();
        return result;
    }

    @Override     
    protected void connectSensors() {
        sensors().set(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)));
        
        String brokerMbeanName = "org.apache.activemq:type=Broker,brokerName=" + getBrokerName();
        
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                        .objectName(brokerMbeanName)
                        .attributeName("BrokerName")
                        .onSuccess(Functions.forPredicate(Predicates.notNull()))
                        .onFailureOrException(Functions.constant(false))
                        .suppressDuplicates(true))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("openWirePort", getAttribute(OPEN_WIRE_PORT));
    }

    @Override
    public Class getDriverInterface() {
        return ActiveMQDriver.class;
    }
}
