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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.messaging.amqp.AmqpServer;
import org.apache.brooklyn.entity.messaging.jms.JMSDestinationImpl;
import org.apache.brooklyn.feed.jmx.JmxFeed;
import org.apache.brooklyn.feed.jmx.JmxHelper;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;

public abstract class QpidDestinationImpl extends JMSDestinationImpl implements QpidDestination {
    public static final Logger log = LoggerFactory.getLogger(QpidDestination.class);
    
    @SetFromFlag
    String virtualHost;

    protected ObjectName virtualHostManager;
    protected ObjectName exchange;
    protected transient JmxHelper jmxHelper;
    protected volatile JmxFeed jmxFeed;

    public QpidDestinationImpl() {
    }

    @Override
    public QpidBroker getParent() {
        return (QpidBroker) super.getParent();
    }
    
    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        
        // TODO Would be nice to share the JmxHelper for all destinations, so just one connection.
        // But tricky for if brooklyn were distributed
        try {
            if (virtualHost == null) virtualHost = getConfig(QpidBroker.VIRTUAL_HOST_NAME);
            sensors().set(QpidBroker.VIRTUAL_HOST_NAME, virtualHost);
            virtualHostManager = new ObjectName(format("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"%s\"", virtualHost));
            jmxHelper = new JmxHelper((EntityLocal)getParent());
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    protected void disconnectSensors() {
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    public void create() {
        jmxHelper.operation(virtualHostManager, "createNewQueue", getName(), getParent().getAttribute(UsesJmx.JMX_USER), true);
        jmxHelper.operation(exchange, "createNewBinding", getName(), getName());
        connectSensors();
    }
    
    @Override
    public void delete() {
        jmxHelper.operation(exchange, "removeBinding", getName(), getName());
        jmxHelper.operation(virtualHostManager, "deleteQueue", getName());
        disconnectSensors();
    }

    @Override
    public String getQueueName() {

        if (AmqpServer.AMQP_0_10.equals(getParent().getAmqpVersion())) {
            return String.format("'%s'/'%s'; { assert: never }", getExchangeName(), getName());
        } else {
            return getName();
        }
    }
}
