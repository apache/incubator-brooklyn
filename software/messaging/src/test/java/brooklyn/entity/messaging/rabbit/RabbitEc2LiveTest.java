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
package brooklyn.entity.messaging.rabbit;

import static org.testng.Assert.assertEquals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
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
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
