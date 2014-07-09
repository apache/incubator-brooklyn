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
package brooklyn.entity.messaging.activemq;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.messaging.jms.JMSDestinationImpl;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.exceptions.Exceptions;

public abstract class ActiveMQDestinationImpl extends JMSDestinationImpl implements ActiveMQDestination {
    protected ObjectName brokerMBeanName;
    protected transient JmxHelper jmxHelper;
    protected volatile JmxFeed jmxFeed;

    public ActiveMQDestinationImpl() {
    }
    
    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        
        //assume just one BrokerName at this endpoint
        try {
            brokerMBeanName = new ObjectName("org.apache.activemq:BrokerName=localhost,Type=Broker");
            jmxHelper = new JmxHelper((EntityLocal) getParent());
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    protected void disconnectSensors() {
        if (jmxFeed != null) jmxFeed.stop();
    }
}
