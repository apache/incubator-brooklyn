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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link ActiveMQBroker} class.
 */
public class ActiveMQIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(ActiveMQIntegrationTest.class);

    private TestApplication app;
    private Location testLocation;
    private ActiveMQBroker activeMQ;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = app.newLocalhostProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() throws Exception {
        activeMQ = app.createAndManageChild(EntitySpec.create(ActiveMQBroker.class));

        activeMQ.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 10*60*1000), activeMQ, Startable.SERVICE_UP, true);
        log.info("JMX URL is "+activeMQ.getAttribute(UsesJmx.JMX_URL));
        activeMQ.stop();
        assertFalse(activeMQ.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly,
     * when a jmx port is supplied
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdownWithCustomJmx() throws Exception {
        activeMQ = app.createAndManageChild(EntitySpec.create(ActiveMQBroker.class)
                .configure("jmxPort", "11099+"));
       
        activeMQ.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 10*60*1000), activeMQ, Startable.SERVICE_UP, true);
        log.info("JMX URL is "+activeMQ.getAttribute(UsesJmx.JMX_URL));
        activeMQ.stop();
        assertFalse(activeMQ.getAttribute(Startable.SERVICE_UP));
    }

    @Test(groups = "Integration")
    public void canStartTwo() throws Exception {
        ActiveMQBroker activeMQ1 = app.createAndManageChild(EntitySpec.create(ActiveMQBroker.class));
        ActiveMQBroker activeMQ2 = app.createAndManageChild(EntitySpec.create(ActiveMQBroker.class));

        activeMQ1.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 10*60*1000), activeMQ1, Startable.SERVICE_UP, true);
        log.info("JMX URL is "+activeMQ1.getAttribute(UsesJmx.JMX_URL));

        activeMQ2.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 10*60*1000), activeMQ2, Startable.SERVICE_UP, true);
        log.info("JMX URL is "+activeMQ2.getAttribute(UsesJmx.JMX_URL));
    }

    /**
     * Test that setting the 'queue' property causes a named queue to be created.
     */
    @Test(groups = "Integration")
    public void testCreatingQueuesDefault() throws Exception {
        String url = testCreatingQueuesInternal(null);
        // localhost default is jmxmp
        Assert.assertTrue(url.contains("jmxmp"), "url="+url);
    }

    @Test(groups = "Integration")
    public void testCreatingQueuesRmi() throws Exception {
        String url = testCreatingQueuesInternal(JmxAgentModes.JMX_RMI_CUSTOM_AGENT);
        Assert.assertTrue(url.contains("rmi://"), "url="+url);
        Assert.assertFalse(url.contains("rmi:///jndi"), "url="+url);
        Assert.assertFalse(url.contains("jmxmp"), "url="+url);
    }

    @Test(groups = "Integration")
    public void testCreatingQueuesJmxmp() throws Exception {
        String url = testCreatingQueuesInternal(JmxAgentModes.JMXMP);
        // localhost default is rmi
        Assert.assertTrue(url.contains("jmxmp"), "url="+url);
        Assert.assertFalse(url.contains("rmi"), "url="+url);
    }

    @Test(groups = "Integration")
    public void testCreatingQueuesNoAgent() throws Exception {
        String url = testCreatingQueuesInternal(JmxAgentModes.NONE);
        // localhost default is rmi
        Assert.assertTrue(url.contains("rmi:///jndi"), "url="+url);
        Assert.assertFalse(url.contains("jmxmp"), "url="+url);
    }

    public String testCreatingQueuesInternal(JmxAgentModes mode) throws Exception {
        String queueName = "testQueue";
        int number = 20;
        String content = "01234567890123456789012345678901";

        // Start broker with a configured queue
        // FIXME Not yet using app.createAndManageChild because later in test do activeMQ.queueNames,
        // which is not on interface
        activeMQ = app.createAndManageChild(EntitySpec.create(ActiveMQBroker.class)
            .configure("queue", queueName)
            .configure(UsesJmx.JMX_AGENT_MODE, mode));
        
        activeMQ.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 10*60*1000), activeMQ, Startable.SERVICE_UP, true);

        String jmxUrl = activeMQ.getAttribute(UsesJmx.JMX_URL);
        log.info("JMX URL ("+mode+") is "+jmxUrl);
        
        try {
            // Check queue created
            assertFalse(activeMQ.getQueueNames().isEmpty());
            assertEquals(activeMQ.getQueueNames().size(), 1);
            assertTrue(activeMQ.getQueueNames().contains(queueName));
            assertEquals(activeMQ.getChildren().size(), 1);
            assertFalse(activeMQ.getQueues().isEmpty());
            assertEquals(activeMQ.getQueues().size(), 1);

            // Get the named queue entity
            ActiveMQQueue queue = activeMQ.getQueues().get(queueName);
            assertNotNull(queue);
            assertEquals(queue.getName(), queueName);

            // Connect to broker using JMS and send messages
            Connection connection = getActiveMQConnection(activeMQ);
            clearQueue(connection, queueName);
            EntityTestUtils.assertAttributeEqualsEventually(queue, ActiveMQQueue.QUEUE_DEPTH_MESSAGES, 0);
            sendMessages(connection, number, queueName, content);
            // Check messages arrived
            EntityTestUtils.assertAttributeEqualsEventually(queue, ActiveMQQueue.QUEUE_DEPTH_MESSAGES, number);

            // Clear the messages
            assertEquals(clearQueue(connection, queueName), number);

            // Check messages cleared
            EntityTestUtils.assertAttributeEqualsEventually(queue, ActiveMQQueue.QUEUE_DEPTH_MESSAGES, 0);

            connection.close();

            // Close the JMS connection
        } finally {
            // Stop broker
            activeMQ.stop();
        }
        
        return jmxUrl;
    }

    private Connection getActiveMQConnection(ActiveMQBroker activeMQ) throws Exception {
        int port = activeMQ.getAttribute(ActiveMQBroker.OPEN_WIRE_PORT);
        String address = activeMQ.getAttribute(ActiveMQBroker.ADDRESS);
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://"+address+":"+port);
        Connection connection = factory.createConnection("admin", "activemq");
        connection.start();
        return connection;
    }

    private void sendMessages(Connection connection, int count, String queueName, String content) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue destination = session.createQueue(queueName);
        MessageProducer messageProducer = session.createProducer(destination);

        for (int i = 0; i < count; i++) {
            TextMessage message = session.createTextMessage(content);
            messageProducer.send(message);
        }

        session.close();
    }

    private int clearQueue(Connection connection, String queueName) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue destination = session.createQueue(queueName);
        MessageConsumer messageConsumer = session.createConsumer(destination);

        int received = 0;
        while (messageConsumer.receive(500) != null) received++;

        session.close();
        
        return received;
    }
}
