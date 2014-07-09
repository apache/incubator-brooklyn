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
import static org.testng.Assert.assertFalse;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

/**
 * Test the operation of the {@link RabbitBroker} class.
 * 
 * TODO If you're having problems running this test successfully, here are a few tips:
 * 
 *  - Is `erl` on your path for a non-interactive ssh session?
 *    Look in rabbit's $RUN_DIR/console-err.log (e.g. /tmp/brooklyn-aled/apps/someappid/entities/RabbitBroker_2.8.7_JROYTcSL/console-err.log)
 *    I worked around that by adding to my ~/.brooklyn/brooklyn.properties:
 *      brooklyn.ssh.config.scriptHeader=#!/bin/bash -e\nif [ -f ~/.bashrc ] ; then . ~/.bashrc ; fi\nif [ -f ~/.profile ] ; then . ~/.profile ; fi\necho $PATH > /tmp/mypath.txt
 *    
 *  - Is the hostname resolving properly?
 *    Look in $RUN_DIR/console-out.log; is there a message like:
 *      ERROR: epmd error for host "Aleds-MacBook-Pro": timeout (timed out establishing tcp connection)
 *    I got around that with disabling my wifi and running when not connected to the internet.
 */
public class RabbitIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(RabbitIntegrationTest.class);

    private TestApplication app;
    private Location testLocation;
    private RabbitBroker rabbit;

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = {"Integration", "WIP"})
    public void canStartupAndShutdown() throws Exception {
        rabbit = app.createAndManageChild(EntitySpec.create(RabbitBroker.class));
        rabbit.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(rabbit, Startable.SERVICE_UP, true);
        rabbit.stop();
        assertFalse(rabbit.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that an AMQP client can connect to and use the broker.
     */
    @Test(groups = {"Integration", "WIP"})
    public void testClientConnection() throws Exception {
        rabbit = app.createAndManageChild(EntitySpec.create(RabbitBroker.class));
        rabbit.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(rabbit, Startable.SERVICE_UP, true);

        byte[] content = "MessageBody".getBytes(Charsets.UTF_8);
        String queue = "queueName";
        Channel producer = null;
        Channel consumer = null;
        try {
	        producer = getAmqpChannel(rabbit);
	        consumer = getAmqpChannel(rabbit);

	        producer.queueDeclare(queue, true, false, false, ImmutableMap.<String,Object>of());
	        producer.queueBind(queue, AmqpExchange.DIRECT, queue);
	        producer.basicPublish(AmqpExchange.DIRECT, queue, null, content);
            
            QueueingConsumer queueConsumer = new QueueingConsumer(consumer);
            consumer.basicConsume(queue, true, queueConsumer);
        
            QueueingConsumer.Delivery delivery = queueConsumer.nextDelivery(60 * 1000l); // one minute timeout
            assertEquals(delivery.getBody(), content);
        } finally {
            closeSafely(producer, 10*1000);
            closeSafely(consumer, 10*1000);
        }
    }

    /**
     * Closes the channel, guaranteeing the call won't hang this thread forever!
     * 
     * Saw this during jenkins testing:
     * "main" prio=10 tid=0x00007f69c8008000 nid=0x5d70 in Object.wait() [0x00007f69d1318000]
     *         java.lang.Thread.State: WAITING (on object monitor)
     *         at java.lang.Object.wait(Native Method)
     *         - waiting on <0x00000000e0947cf8> (a com.rabbitmq.utility.BlockingValueOrException)
     *         at java.lang.Object.wait(Object.java:502)
     *         at com.rabbitmq.utility.BlockingCell.get(BlockingCell.java:50)
     *         - locked <0x00000000e0947cf8> (a com.rabbitmq.utility.BlockingValueOrException)
     *         at com.rabbitmq.utility.BlockingCell.get(BlockingCell.java:65)
     *         - locked <0x00000000e0947cf8> (a com.rabbitmq.utility.BlockingValueOrException)
     *         at com.rabbitmq.utility.BlockingCell.uninterruptibleGet(BlockingCell.java:111)
     *         - locked <0x00000000e0947cf8> (a com.rabbitmq.utility.BlockingValueOrException)
     *         at com.rabbitmq.utility.BlockingValueOrException.uninterruptibleGetValue(BlockingValueOrException.java:37)
     *         at com.rabbitmq.client.impl.AMQChannel$BlockingRpcContinuation.getReply(AMQChannel.java:349)
     *         at com.rabbitmq.client.impl.ChannelN.close(ChannelN.java:543)
     *         at com.rabbitmq.client.impl.ChannelN.close(ChannelN.java:480)
     *         at com.rabbitmq.client.impl.ChannelN.close(ChannelN.java:473)
     *         at com.rabbitmq.client.Channel$close.call(Unknown Source)
     *         at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall(CallSiteArray.java:42)
     *         at org.codehaus.groovy.runtime.callsite.AbstractCallSite.call(AbstractCallSite.java:108)
     *         at org.codehaus.groovy.runtime.callsite.AbstractCallSite.call(AbstractCallSite.java:112)
     *         at org.codehaus.groovy.runtime.callsite.AbstractCallSite.callSafe(AbstractCallSite.java:75)
     *         at brooklyn.entity.messaging.rabbit.RabbitIntegrationTest.testClientConnection(RabbitIntegrationTest.groovy:107)
     */
    private void closeSafely(final Channel channel, int timeoutMs) throws InterruptedException {
        if (channel == null) return;
        Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        log.error("Error closing RabbitMQ Channel; continuing", e);
                    }
                }});
        try {
            t.start();
            t.join(timeoutMs);
            
            if (t.isAlive()) {
                log.error("Timeout when closing RabbitMQ Channel "+channel+"; aborting close and continuing");
            }
        } finally {
            t.interrupt();
            t.join(1*1000);
            if (t.isAlive()) t.stop();
        }
    }
    
    private Channel getAmqpChannel(RabbitBroker rabbit) throws Exception {
        String uri = rabbit.getAttribute(MessageBroker.BROKER_URL);
        log.warn("connecting to rabbit {}", uri);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(uri);
        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();
        return channel;
    }
}
