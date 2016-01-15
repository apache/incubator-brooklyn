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
package org.apache.brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.entity.java.JmxSupport;
import org.apache.brooklyn.entity.messaging.jms.JMSBrokerImpl;
import org.apache.brooklyn.feed.jmx.JmxAttributePollConfig;
import org.apache.brooklyn.feed.jmx.JmxFeed;
import org.apache.brooklyn.feed.jmx.JmxHelper;
import org.apache.brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Qpid broker instance, using AMQP 0-10.
 */
public class QpidBrokerImpl extends JMSBrokerImpl<QpidQueue, QpidTopic> implements QpidBroker {
    private static final Logger log = LoggerFactory.getLogger(QpidBrokerImpl.class);

    private volatile JmxFeed jmxFeed;

    public QpidBrokerImpl() {
        super();
    }

    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME); }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION); }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT); }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@/%s?brokerlist='tcp://%s:%d'";
        sensors().set(BROKER_URL, format(urlFormat, getAttribute(VIRTUAL_HOST_NAME), getAttribute(HOSTNAME), getAttribute(AMQP_PORT)));
    }
    
    @Override
    public void init() {
        super.init();
        new JmxSupport(this, null).recommendJmxRmiCustomAgent();
    }

    public void waitForServiceUp(long duration, TimeUnit units) {
        super.waitForServiceUp(duration, units);

        // Also wait for the MBean to exist (as used when creating queue/topic)
        JmxHelper helper = new JmxHelper(this);
        try {
            String virtualHost = getConfig(QpidBroker.VIRTUAL_HOST_NAME);
            ObjectName virtualHostManager = new ObjectName(format("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"%s\"", virtualHost));
            helper.connect();
            helper.assertMBeanExistsEventually(virtualHostManager, units.toMillis(duration));
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            if (helper != null) helper.terminate();
        }
    }
    
    public QpidQueue createQueue(Map properties) {
        QpidQueue result = addChild(EntitySpec.create(QpidQueue.class).configure(properties));
        result.create();
        return result;
    }

    public QpidTopic createTopic(Map properties) {
        QpidTopic result = addChild(EntitySpec.create(QpidTopic.class).configure(properties));
        result.create();
        return result;
    }

    @Override
    public Class getDriverInterface() {
        return QpidDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        String serverInfoMBeanName = "org.apache.qpid:type=ServerInformation,name=ServerInformation";

        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                        .objectName(serverInfoMBeanName)
                        .attributeName("ProductVersion")
                        .onSuccess(new Function<Object,Boolean>() {
                                private boolean hasWarnedOfVersionMismatch;
                                @Override public Boolean apply(Object input) {
                                    if (input == null) return false;
                                    if (!hasWarnedOfVersionMismatch && !getConfig(QpidBroker.SUGGESTED_VERSION).equals(input)) {
                                        log.warn("Qpid version mismatch: ProductVersion is {}, requested version is {}", input, getConfig(QpidBroker.SUGGESTED_VERSION));
                                        hasWarnedOfVersionMismatch = true;
                                    }
                                    return true;
                                }})
                        .onException(Functions.constant(false))
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
        return super.toStringHelper().add("amqpPort", getAmqpPort());
    }
}
