/*
 * Copyright 2012-2013 by Cloudsoft Corp.
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
package brooklyn.entity.nosql.couchdb;

import static org.testng.Assert.assertEquals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * A live test of the {@link CouchDBCluster} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CouchDBNode}
 * can be read from another, using the Astyanax API.
 */
class CouchDBClusterLiveTest {

    private static final Logger log = LoggerFactory.getLogger(CouchDBClusterLiveTest.class);

    // private String provider = "rackspace-cloudservers-uk";
    private String provider = "aws-ec2:eu-west-1";

    protected TestApplication app;
    protected Location testLocation;
    protected CouchDBCluster cluster;

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
     * Test that a two node cluster starts up and allows access via the Astyanax API through both nodes.
     */
    @Test(groups = "Live")
    public void canStartupAndShutdown() throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CouchDBCluster.class)
                .configure("initialSize", 2)
                .configure("clusterName", "AmazonCluster"));
        assertEquals(cluster.getCurrentSize().intValue(), 0);

        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, CouchDBCluster.GROUP_SIZE, 2);
        Entities.dumpInfo(app);

        CouchDBNode first = (CouchDBNode) Iterables.get(cluster.getMembers(), 0);
        CouchDBNode second = (CouchDBNode) Iterables.get(cluster.getMembers(), 1);

        EntityTestUtils.assertAttributeEqualsEventually(first, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, Startable.SERVICE_UP, true);

        JcouchdbSupport jcouchdbFirst = new JcouchdbSupport(first);
        JcouchdbSupport jcouchdbSecond = new JcouchdbSupport(second);
        jcouchdbFirst.jcouchdbTest();
        jcouchdbSecond.jcouchdbTest();
    }
}
