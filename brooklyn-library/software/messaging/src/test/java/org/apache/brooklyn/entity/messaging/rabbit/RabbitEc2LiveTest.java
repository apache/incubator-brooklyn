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
package org.apache.brooklyn.entity.messaging.rabbit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.entity.AbstractEc2LiveTest;
import org.apache.brooklyn.entity.messaging.MessageBroker;
import org.apache.brooklyn.entity.messaging.amqp.AmqpExchange;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

public class RabbitEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        RabbitBroker rabbit = app.createAndManageChild(EntitySpec.create(RabbitBroker.class));
        rabbit.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(rabbit, RabbitBroker.SERVICE_UP, true);

        byte[] content = "MessageBody".getBytes(Charsets.UTF_8);
        String queue = "queueName";
        Channel producer = null;
        Channel consumer = null;
        try {
            producer = getAmqpChannel(rabbit);
            consumer = getAmqpChannel(rabbit);

            producer.queueDeclare(queue, true, false, false, Maps.<String,Object>newHashMap());
            producer.queueBind(queue, AmqpExchange.DIRECT, queue);
            producer.basicPublish(AmqpExchange.DIRECT, queue, null, content);
            
            QueueingConsumer queueConsumer = new QueueingConsumer(consumer);
            consumer.basicConsume(queue, true, queueConsumer);
        
            QueueingConsumer.Delivery delivery = queueConsumer.nextDelivery();
            assertEquals(delivery.getBody(), content);
        } finally {
            if (producer != null) producer.close();
            if (consumer != null) consumer.close();
        }
    }

    private Channel getAmqpChannel(RabbitBroker rabbit) throws Exception {
        String uri = rabbit.getAttribute(MessageBroker.BROKER_URL);
        LOG.warn("connecting to rabbit {}", uri);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(uri);
        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();
        return channel;
    }

    @Override
    public void test_CentOS_5() throws SkipException {
        // Not supported. The EPEL repository described here at [1] does not contain erlang, and the
        // Erlang repository at [1] requires old versions of rpmlib. Additionally, [2] suggests that
        // Centos 5 is not supported
        // [1]:http://www.rabbitmq.com/install-rpm.html
        // [2]: https://www.erlang-solutions.com/downloads/download-erlang-otp
        throw new SkipException("Centos 5 is not supported");
    }

    @Test(groups = {"Live"})
    public void testWithOnlyPort22() throws Exception {
        // CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1
        jcloudsLocation = mgmt.getLocationRegistry().resolve(LOCATION_SPEC, ImmutableMap.of(
                "tags", ImmutableList.of(getClass().getName()),
                "imageId", "us-east-1/ami-a96b01c0", 
                "hardwareId", SMALL_HARDWARE_ID));

        final RabbitBroker server = app.createAndManageChild(EntitySpec.create(RabbitBroker.class)
                .configure(RabbitBroker.PROVISIONING_PROPERTIES.subKey(CloudLocationConfig.INBOUND_PORTS.getName()), ImmutableList.of(22)));
        
        app.start(ImmutableList.of(jcloudsLocation));
        
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        Integer port = server.getAttribute(RabbitBroker.AMQP_PORT);
        assertNotNull(port);
        
        assertViaSshLocalPortListeningEventually(server, port);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
