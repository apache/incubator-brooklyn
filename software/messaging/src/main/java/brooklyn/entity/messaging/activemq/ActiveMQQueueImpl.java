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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;

public class ActiveMQQueueImpl extends ActiveMQDestinationImpl implements ActiveMQQueue {
    public static final Logger log = LoggerFactory.getLogger(ActiveMQQueue.class);

    public ActiveMQQueueImpl() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        setAttribute(QUEUE_NAME, getName());
    }

    public String getQueueName() {
        return getName();
    }
    
    public void create() {
        if (log.isDebugEnabled()) log.debug("{} adding queue {} to broker {}", new Object[] {this, getName(), jmxHelper.getAttribute(brokerMBeanName, "BrokerId")});
        
        jmxHelper.operation(brokerMBeanName, "addQueue", getName());
        
        connectSensors();
    }

    public void delete() {
        jmxHelper.operation(brokerMBeanName, "removeQueue", getName());
        disconnectSensors();
    }

    @Override
    protected void connectSensors() {
        String queue = String.format("org.apache.activemq:BrokerName=localhost,Type=Queue,Destination=%s", getName());
        
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .helper(jmxHelper)
                .pollAttribute(new JmxAttributePollConfig<Integer>(QUEUE_DEPTH_MESSAGES)
                        .objectName(queue)
                        .attributeName("QueueSize"))
                .build();
    }
}
