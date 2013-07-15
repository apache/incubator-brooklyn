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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.messaging.activemq.ActiveMQBroker;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

/**
 * Test the operation of the {@link ActiveMQBroker} class.
 *
 * TODO test that sensors update.
 */
public class KafkaIntegrationTest {

    private TestApplication app;
    private Location testLocation;

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that we can start a zookeeper.
     */
    @Test(groups = "Integration")
    public void testZookeeper() {
        final KafkaZookeeper zookeeper = app.createAndManageChild(EntitySpecs.spec(KafkaZookeeper.class));

        zookeeper.start(ImmutableList.of(testLocation));
        Asserts.succeedsEventually(MutableMap.of("timeout", 60000l), new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(zookeeper.getAttribute(Startable.SERVICE_UP));
                return null;
            }
        });

        zookeeper.stop();
        assertFalse(zookeeper.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that we can start a  broker and zookeeper together.
     */
    @Test(groups = "Integration")
    public void testBrokerPlusZookeeper() {
        final KafkaZookeeper zookeeper = app.createAndManageChild(EntitySpecs.spec(KafkaZookeeper.class));
        final KafkaBroker broker = app.createAndManageChild(EntitySpecs.spec(KafkaBroker.class).configure(KafkaBroker.ZOOKEEPER, zookeeper));

        zookeeper.start(ImmutableList.of(testLocation));
        Asserts.succeedsEventually(MutableMap.of("timeout", 60000l), new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(zookeeper.getAttribute(Startable.SERVICE_UP));
                return null;
            }
        });

        broker.start(ImmutableList.of(testLocation));
        Asserts.succeedsEventually(MutableMap.of("timeout", 60000l), new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(broker.getAttribute(Startable.SERVICE_UP));
                return null;
            }
        });

        zookeeper.stop();
        assertFalse(zookeeper.getAttribute(Startable.SERVICE_UP));

        broker.stop();
        assertFalse(broker.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that we can start a cluster with zookeeper and one broker.
     *
     * Connects to the zookeeper controller and tests sending and receiving messages on a topic.
     */
    @Test(groups = "Integration")
    public void testSingleBrokerCluster() {
        final KafkaCluster cluster = app.createAndManageChild(EntitySpecs.spec(KafkaCluster.class));

        cluster.start(ImmutableList.of(testLocation));
        Asserts.succeedsEventually(MutableMap.of("timeout", 60000l), new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(cluster.getAttribute(Startable.SERVICE_UP));
                assertTrue(cluster.getZookeeper().getAttribute(Startable.SERVICE_UP));
                assertEquals(cluster.getCurrentSize().intValue(), 1);
                return null;
            }
        });

        Entities.dumpInfo(cluster);

        KafkaSupport support = new KafkaSupport(cluster);

        support.sendMessage("brooklyn", "TEST_MESSAGE");
        String message = support.getMessage("brooklyn");
        assertEquals(message, "TEST_MESSAGE");
    }

}
