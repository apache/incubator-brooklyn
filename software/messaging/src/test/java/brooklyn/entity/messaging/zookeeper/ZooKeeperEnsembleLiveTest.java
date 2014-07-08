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
package brooklyn.entity.messaging.zookeeper;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.ZooKeeperEnsemble;
import brooklyn.entity.zookeeper.ZooKeeperNode;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * A live test of the {@link brooklyn.entity.zookeeper.ZooKeeperEnsemble} entity.
 *
 * Tests that a 3 node cluster can be started on Amazon EC2 and data written on one {@link brooklyn.entity.zookeeper.ZooKeeperEnsemble}
 * can be read from another, using the Astyanax API.
 */
public class ZooKeeperEnsembleLiveTest {

    private static final Logger log = LoggerFactory.getLogger(ZooKeeperEnsembleLiveTest.class);
    
    private String provider = 
            "gce-europe-west1";
//            "aws-ec2:eu-west-1";
//            "named:hpcloud-compute-at";
//            "localhost";

    protected TestApplication app;
    protected Location testLocation;
    protected ZooKeeperEnsemble cluster;

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider);
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that a two node cluster starts up and allows access through both nodes.
     */
    @Test(groups = "Live")
    public void testStartUpConnectAndResize() throws Exception {
        try {
            cluster = app.createAndManageChild(EntitySpec.create(ZooKeeperEnsemble.class)
                    .configure("initialSize", 3)
                    .configure("clusterName", "ZooKeeperEnsembleLiveTest"));
            assertEquals(cluster.getCurrentSize().intValue(), 0);

            app.start(ImmutableList.of(testLocation));

            EntityTestUtils.assertAttributeEqualsEventually(cluster, ZooKeeperEnsemble.GROUP_SIZE, 3);
            Entities.dumpInfo(app);

            EntityTestUtils.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, true);
            for(Entity zkNode : cluster.getMembers()) {
                assertTrue(isSocketOpen((ZooKeeperNode) zkNode));
            }
            cluster.resize(1);
            EntityTestUtils.assertAttributeEqualsEventually(cluster, ZooKeeperEnsemble.GROUP_SIZE, 1);
            Entities.dumpInfo(app);
            EntityTestUtils.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, true);
            for (Entity zkNode : cluster.getMembers()) {
                assertTrue(isSocketOpen((ZooKeeperNode) zkNode));
            }
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
    }

    protected static boolean isSocketOpen(ZooKeeperNode node) {
        int attempt = 0, maxAttempts = 20;
        while(attempt < maxAttempts) {
            try {
                Socket s = new Socket(node.getAttribute(Attributes.HOSTNAME), node.getZookeeperPort());
                s.close();
                return true;
            } catch (Exception e) {
                attempt++;
            }
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        }
        return false;
    }
    
}
