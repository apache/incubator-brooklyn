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
package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertNotNull;

import java.io.File;
import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class CassandraDatacenterRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraDatacenterRebindIntegrationTest.class);

    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private ClassLoader classLoader = getClass().getClassLoader();
    private LocalManagementContext origManagementContext;
    private File mementoDir;
    private TestApplication origApp;
    private TestApplication newApp;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        mementoDir = Files.createTempDir();
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        origApp = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);

        localhostProvisioningLocation = origApp.newLocalhostProvisioningLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (newApp != null) Entities.destroyAllCatching(newApp.getManagementContext());
        if (origApp != null && origManagementContext.isRunning()) Entities.destroyAll(origManagementContext);
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        RebindTestUtils.checkCurrentMementoSerializable(origApp);
        
        // Stop the old management context, so original entities won't interfere
        origManagementContext.terminate();
        
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }

    /**
     * Test that Brooklyn can rebind to a single node datacenter.
     */
    @Test(groups = "Integration")
    public void testRebindDatacenterOfSizeOne() throws Exception {
        CassandraDatacenter origDatacenter = origApp.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                .configure("initialSize", 1));

        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        CassandraNode origNode = (CassandraNode) Iterables.get(origDatacenter.getMembers(), 0);

        EntityTestUtils.assertAttributeEqualsEventually(origDatacenter, CassandraDatacenter.GROUP_SIZE, 1);
        EntityTestUtils.assertAttributeEqualsEventually(origNode, Startable.SERVICE_UP, true);
        assertConsistentVersionAndPeersEventually(origNode);
        EntityTestUtils.assertAttributeEquals(origNode, CassandraNode.PEERS, 1);
        CassandraDatacenterLiveTest.checkConnectionRepeatedly(2, 5, origNode, origNode);
        BigInteger origToken = origNode.getAttribute(CassandraNode.TOKEN);
        assertNotNull(origToken);
        
        newApp = rebind();
        final CassandraDatacenter newDatacenter = (CassandraDatacenter) Iterables.find(newApp.getChildren(), Predicates.instanceOf(CassandraDatacenter.class));
        final CassandraNode newNode = (CassandraNode) Iterables.find(newDatacenter.getMembers(), Predicates.instanceOf(CassandraNode.class));
        
        EntityTestUtils.assertAttributeEqualsEventually(newDatacenter, CassandraDatacenter.GROUP_SIZE, 1);
        EntityTestUtils.assertAttributeEqualsEventually(newNode, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(newNode, CassandraNode.TOKEN, origToken);
        assertConsistentVersionAndPeersEventually(newNode);
        EntityTestUtils.assertAttributeEquals(newNode, CassandraNode.PEERS, 1);
        CassandraDatacenterLiveTest.checkConnectionRepeatedly(2, 5, newNode, newNode);
    }
    
    protected void assertConsistentVersionAndPeersEventually(CassandraNode node) {
        // may take some time to be consistent (with new thrift_latency checks on the node,
        // contactability should not be an issue, but consistency still might be)
        for (int i=0; ; i++) {
            boolean open = CassandraDatacenterLiveTest.isSocketOpen(node);
            Boolean consistant = open ? CassandraDatacenterLiveTest.areVersionsConsistent(node) : null;
            Integer numPeers = node.getAttribute(CassandraNode.PEERS);
            String msg = "consistency:  "
                    + (!open ? "unreachable" : consistant==null ? "error" : consistant)+"; "
                    + "peer group sizes: "+numPeers;
            LOG.info(msg);
            if (open && Boolean.TRUE.equals(consistant) && numPeers==1)
                break;
            if (i == 0) LOG.warn("NOT yet consistent, waiting");
            if (i >= 120) Assert.fail("Did not become consistent in time: "+msg);
            Time.sleep(Duration.ONE_SECOND);
        }
    }
}
