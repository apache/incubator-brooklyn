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
package brooklyn.entity.messaging.qpid;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.configuration.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.trait.Startable;
import org.apache.brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;

/**
 * Test the operation of the {@link QpidBroker} class.
 */
public class QpidIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(QpidIntegrationTest.class);

    private TestApplication app;
    private Location testLocation;
    private QpidBroker qpid;

    @BeforeMethod(groups = "Integration")
    public void setup() {
        String workingDir = System.getProperty("user.dir");
        log.info("Qpid working dir: {}", workingDir);
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = app.newLocalhostProvisioningLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that the broker starts up with JMX and RMI ports configured, and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        qpid = app.createAndManageChild(EntitySpec.create(QpidBroker.class)
                .configure("jmxPort", "9909+")
                .configure("rmiRegistryPort", "9910+"));
        qpid.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(qpid, Startable.SERVICE_UP, true);
        qpid.stop();
        assertFalse(qpid.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that the broker starts up with HTTP management enabled, and we can connect to the URL.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdownWithHttpManagement() {
        qpid = app.createAndManageChild(EntitySpec.create(QpidBroker.class)
                .configure("httpManagementPort", "8888+"));
        qpid.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(qpid, Startable.SERVICE_UP, true);
        String httpUrl = "http://"+qpid.getAttribute(QpidBroker.HOSTNAME)+":"+qpid.getAttribute(QpidBroker.HTTP_MANAGEMENT_PORT)+"/management";
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpUrl, 200);
        // TODO check actual REST output
        qpid.stop();
        assertFalse(qpid.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly when plugins are configured.
     * 
     * FIXME the custom plugin was written against qpid 0.14, so that's the version we need to run
     * this test against. However, v0.14 is no longer available from the download site.
     * We should update this plugin so it works with the latest qpid.
     */
    @Test(enabled = false, groups = "Integration")
    public void canStartupAndShutdownWithPlugin() {
        Map<String,String> qpidRuntimeFiles = MutableMap.<String,String>builder()
                .put("classpath://qpid-test-config.xml", "etc/config.xml")
                .put("http://developers.cloudsoftcorp.com/brooklyn/repository-test/0.7.0/QpidBroker/qpid-test-plugin.jar", "lib/plugins/sample-plugin.jar")
                .build();
        qpid = app.createAndManageChild(EntitySpec.create(QpidBroker.class)
                .configure(SoftwareProcess.RUNTIME_FILES, qpidRuntimeFiles)
                .configure(QpidBroker.SUGGESTED_VERSION, "0.14"));
        qpid.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(qpid, Startable.SERVICE_UP, true);
        qpid.stop();
        assertFalse(qpid.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that setting the 'queue' property causes a named queue to be created.
     *
     * This test is disabled, pending further investigation. Issue with AMQP 0-10 queue names.
     * 
     * FIXME disabled becausing failing in jenkins CI (in QpidIntegrationTest.getQpidConnection()).
     *     url=amqp://admin:********@brooklyn/localhost?brokerlist='tcp://localhost:5672'
     * Was previously enabled, dispite comment above about "test is disabled".	
     */
    @Test(enabled = false, groups = { "Integration", "WIP" })
    public void testCreatingQueues() {
        final String queueName = "testQueue";
        final int number = 20;
        final String content = "01234567890123456789012345678901";

        // Start broker with a configured queue
        // FIXME Can't use app.createAndManageChild, because of QpidDestination reffing impl directly
        qpid = app.createAndManageChild(EntitySpec.create(QpidBroker.class)
                .configure("queue", queueName));
        qpid.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(qpid, Startable.SERVICE_UP, true);

        try {
            // Check queue created
            assertFalse(qpid.getQueueNames().isEmpty());
            assertEquals(qpid.getQueueNames().size(), 1);
            assertTrue(qpid.getQueueNames().contains(queueName));
            assertEquals(qpid.getChildren().size(), 1);
            assertFalse(qpid.getQueues().isEmpty());
            assertEquals(qpid.getQueues().size(), 1);

            // Get the named queue entity
            final QpidQueue queue = qpid.getQueues().get(queueName);
            assertNotNull(queue);

            // Connect to broker using JMS and send messages
            Connection connection = getQpidConnection(qpid);
            clearQueue(connection, queue.getQueueName());
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertEquals(queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), Integer.valueOf(0));
                }
            });
            sendMessages(connection, number, queue.getQueueName(), content);

            // Check messages arrived
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertEquals(queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), Integer.valueOf(number));
                    assertEquals(queue.getAttribute(QpidQueue.QUEUE_DEPTH_BYTES), Integer.valueOf(number * content.length()));
                }
            });

            //TODO clearing the queue currently returns 0
//            // Clear the messages -- should get 20
//            assertEquals clearQueue(connection, queue.queueName), 20
//
//            // Check messages cleared
//            executeUntilSucceeds {
//                assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), 0
//                assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_BYTES), 0
//            }

            // Close the JMS connection
            connection.close();
        } catch (JMSException jmse) {
            log.warn("JMS exception caught", jmse);
            throw Exceptions.propagate(jmse);
        } finally {
            // Stop broker
            qpid.stop();
            qpid = null;
            app = null;
        }
    }

    private Connection getQpidConnection(QpidBroker qpid) {
        int port = qpid.getAttribute(Attributes.AMQP_PORT);
        System.setProperty(ClientProperties.AMQP_VERSION, "0-10");
        System.setProperty(ClientProperties.DEST_SYNTAX, "ADDR");
        String connectionUrl = String.format("amqp://admin:admin@brooklyn/localhost?brokerlist='tcp://localhost:%d'", port);
        try {
            AMQConnectionFactory factory = new AMQConnectionFactory(connectionUrl);
            Connection connection = factory.createConnection();
            connection.start();
            return connection;
        } catch (Exception e) {
            log.error(String.format("Error connecting to qpid: %s", connectionUrl), e);
            throw Exceptions.propagate(e);
        }
    }

    private void sendMessages(Connection connection, int count, String queueName, String content) throws JMSException {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue destination = session.createQueue(queueName);
        MessageProducer messageProducer = session.createProducer(destination);

        for (int i = 0; i < count; i++) {
            TextMessage message = session.createTextMessage(content);
            messageProducer.send(message);
        }

        session.close();
    }

    private int clearQueue(Connection connection, String queueName) throws JMSException {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue destination = session.createQueue(queueName);
        MessageConsumer messageConsumer = session.createConsumer(destination);

        int received = 0;
        while (messageConsumer.receive(500) != null) received++;

        session.close();

        return received;
    }
}
