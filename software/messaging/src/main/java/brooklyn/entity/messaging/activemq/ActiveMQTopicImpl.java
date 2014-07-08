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


public class ActiveMQTopicImpl extends ActiveMQDestinationImpl implements ActiveMQTopic {
    public ActiveMQTopicImpl() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        setAttribute(TOPIC_NAME, getName());
    }

    @Override
    public void create() {
        jmxHelper.operation(brokerMBeanName, "addTopic", getName());
        connectSensors();
    }

    public void delete() {
        jmxHelper.operation(brokerMBeanName, "removeTopic", getName());
        disconnectSensors();
    }

    public void connectSensors() {
        //TODO add sensors for topics
    }

    public String getTopicName() {
        return getName();
    }
}
