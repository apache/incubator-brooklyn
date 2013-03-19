/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.messaging.kafka;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage

import org.apache.activemq.ActiveMQConnectionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link ActiveMQBroker} class.
 */
public class KafkaIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(KafkaIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private Location testLocation

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        testLocation = new LocalhostMachineProvisioningLocation()
    }

    @AfterMethod(groups = "Integration")
    public void shutdown() {
        if (app != null) Entities.destroyAll(app);
    }

    /**
     * Test that we can start a zookeeper.
     */
    @Test(groups = "Integration")
    public void testZookeeper() {
        KafkaZookeeper zookeeper = app.createAndManageChild(BasicEntitySpec.newInstance(KafkaZookeeper.class));

        zookeeper.start([ testLocation ])
        executeUntilSucceedsWithShutdown(zookeeper, timeout:600*TimeUnit.SECONDS) {
            assertTrue zookeeper.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse zookeeper.getAttribute(Startable.SERVICE_UP)
    }

    /**
     * Test that we can start a  broker and zookeeper together.
     */
    @Test(groups = "Integration")
    public void testBrokerPlusZookeeper() {
        KafkaZookeeper zookeeper = app.createAndManageChild(BasicEntitySpec.newInstance(KafkaZookeeper.class));
        KafkaBroker broker = app.createAndManageChild(BasicEntitySpec.newInstance(KafkaBroker.class).configure(KafkaBroker.ZOOKEEPER, zookeeper));

        zookeeper.start([ testLocation ])
        executeUntilSucceeds(timeout:600*TimeUnit.SECONDS) {
            assertTrue zookeeper.getAttribute(Startable.SERVICE_UP)
        }
    
        broker.start([ testLocation ])
        executeUntilSucceeds(timeout:600*TimeUnit.SECONDS) {
            assertTrue broker.getAttribute(Startable.SERVICE_UP)
        }
    }

    /**
     * Test that we can start a cluster with zookeeper and one broker.
     */
    @Test(groups = "Integration")
    public void testSingleBrokerCluster() {
        KafkaCluster cluster = app.createAndManageChild(BasicEntitySpec.newInstance(KafkaCluster.class).configure(KafkaCluster.INITIAL_SIZE, 1));

        cluster.start([ testLocation ])
        executeUntilSucceedsWithShutdown(cluster, timeout:600*TimeUnit.SECONDS) {
            assertTrue cluster.getAttribute(Startable.SERVICE_UP)
            Entities.dumpInfo(cluster)
        }
        assertFalse cluster.getAttribute(Startable.SERVICE_UP)
    }

    // TODO test with API sending messages
    // TODO test that sensors update
    // TODO add demo application
}
