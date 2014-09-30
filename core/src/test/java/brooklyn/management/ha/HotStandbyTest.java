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
package brooklyn.management.ha;

import static org.testng.Assert.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.ListeningObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.location.Location;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.Iterables;

@Test
public class HotStandbyTest {

    private static final Logger log = LoggerFactory.getLogger(HotStandbyTest.class);
    
    private List<HaMgmtNode> nodes = new MutableList<HotStandbyTest.HaMgmtNode>();
    Map<String,String> sharedBackingStore = MutableMap.of();
    Map<String,Date> sharedBackingStoreDates = MutableMap.of();
    private ClassLoader classLoader = getClass().getClassLoader();
    
    public class HaMgmtNode {
        
        private ManagementContextInternal mgmt;
        private String ownNodeId;
        private String nodeName;
        private ListeningObjectStore objectStore;
        private ManagementPlaneSyncRecordPersister persister;
        private HighAvailabilityManagerImpl ha;

        @BeforeMethod(alwaysRun=true)
        public void setUp() throws Exception {
            nodeName = "node "+nodes.size();
            mgmt = newLocalManagementContext();
            ownNodeId = mgmt.getManagementNodeId();
            objectStore = new ListeningObjectStore(newPersistenceObjectStore());
            objectStore.injectManagementContext(mgmt);
            objectStore.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
            persister = new ManagementPlaneSyncRecordPersisterToObjectStore(mgmt, objectStore, classLoader);
            ((ManagementPlaneSyncRecordPersisterToObjectStore)persister).allowRemoteTimestampInMemento();
            BrooklynMementoPersisterToObjectStore persisterObj = new BrooklynMementoPersisterToObjectStore(objectStore, mgmt.getBrooklynProperties(), classLoader);
            mgmt.getRebindManager().setPersister(persisterObj, PersistenceExceptionHandlerImpl.builder().build());
            ha = ((HighAvailabilityManagerImpl)mgmt.getHighAvailabilityManager())
                .setPollPeriod(Duration.PRACTICALLY_FOREVER)
                .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
                .setPersister(persister);
            log.info("Created "+nodeName+" "+ownNodeId);
        }
        
        public void tearDown() throws Exception {
            if (ha != null) ha.stop();
            if (mgmt!=null) mgmt.getRebindManager().stop();
            
            if (mgmt != null) Entities.destroyAll(mgmt);
            if (objectStore != null) objectStore.deleteCompletely();
        }
        
        @Override
        public String toString() {
            return nodeName+" "+ownNodeId;
        }
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        nodes.clear();
        sharedBackingStore.clear();
    }
    
    public HaMgmtNode newNode() throws Exception {
        HaMgmtNode node = new HaMgmtNode();
        node.setUp();
        nodes.add(node);
        return node;
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (HaMgmtNode n: nodes)
            n.tearDown();
    }

    protected ManagementContextInternal newLocalManagementContext() {
        return new LocalManagementContextForTests();
    }

    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore(sharedBackingStore, sharedBackingStoreDates);
    }

    private HaMgmtNode createMaster() throws Exception {
        HaMgmtNode n1 = newNode();
        n1.ha.start(HighAvailabilityMode.AUTO);
        assertEquals(n1.ha.getNodeState(), ManagementNodeState.MASTER);
        return n1;
    }
    
    private HaMgmtNode createHotStandby() throws Exception {
        HaMgmtNode n2 = newNode();
        n2.ha.start(HighAvailabilityMode.HOT_STANDBY);
        assertEquals(n2.ha.getNodeState(), ManagementNodeState.HOT_STANDBY);
        return n2;
    }

    private TestApplication createFirstAppAndPersist(HaMgmtNode n1) throws Exception {        
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(n1.mgmt);
        app.setDisplayName("First App");
        app.start(MutableList.<Location>of());
        app.setConfig(TestEntity.CONF_NAME, "first-app");
        app.setAttribute(TestEntity.SEQUENCE, 3);
        
        n1.mgmt.getRebindManager().forcePersistNow();
        return app;
    }
    
    private Application waitForRebindSequenceNumber(HaMgmtNode master, HaMgmtNode hotStandby, Application app, int expectedSensorSequenceValue) {
        Time.sleep(Duration.FIVE_SECONDS);
        Application appRO = hotStandby.mgmt.lookup(app.getId(), Application.class);
        // TODO drop the sleep and the new lookup, when the implementation can do in-place replace
        
        EntityTestUtils.assertAttributeEqualsEventually(appRO, TestEntity.SEQUENCE, expectedSensorSequenceValue);
        return appRO;
    }
    
    @Test
    public void testHotStandbySeesChangedNameConfigAndSensorValueButDoesntAllowChange() throws Exception {
        HaMgmtNode n1 = createMaster();
        TestApplication app = createFirstAppAndPersist(n1);
        HaMgmtNode n2 = createHotStandby();

        assertEquals(n2.mgmt.getApplications().size(), 1);
        Application appRO = n2.mgmt.lookup(app.getId(), Application.class);
        Assert.assertNotNull(appRO);
        Assert.assertTrue(appRO instanceof TestApplication);
        assertEquals(appRO.getDisplayName(), "First App");
        assertEquals(appRO.getConfig(TestEntity.CONF_NAME), "first-app");
        assertEquals(appRO.getAttribute(TestEntity.SEQUENCE), (Integer)3);

        try {
            ((TestApplication)appRO).setAttribute(TestEntity.SEQUENCE, 4);
            Assert.fail("Should not have allowed sensor to be set");
        } catch (Exception e) {
            Assert.assertTrue(e.toString().toLowerCase().contains("read-only"), "Error message did not contain expected text: "+e);
        }
    }

    public void testHotStandbySeesChangedNameConfigAndSensorValue() throws Exception {
        HaMgmtNode n1 = createMaster();
        TestApplication app = createFirstAppAndPersist(n1);
        HaMgmtNode n2 = createHotStandby();

        assertEquals(n2.mgmt.getApplications().size(), 1);
        Application appRO = n2.mgmt.lookup(app.getId(), Application.class);
        Assert.assertNotNull(appRO);
        assertEquals(appRO.getChildren().size(), 0);
        
        // test changes

        app.setDisplayName("First App Renamed");
        app.setConfig(TestEntity.CONF_NAME, "first-app-renamed");
        app.setAttribute(TestEntity.SEQUENCE, 4);

        appRO = waitForRebindSequenceNumber(n1, n2, app, 4);
        assertEquals(n2.mgmt.getEntityManager().getEntities().size(), 1);
        assertEquals(appRO.getDisplayName(), "First App Renamed");
        assertEquals(appRO.getConfig(TestEntity.CONF_NAME), "first-app-renamed");
        
        // and change again for good measure!

        app.setDisplayName("First App");
        app.setConfig(TestEntity.CONF_NAME, "first-app-restored");
        app.setAttribute(TestEntity.SEQUENCE, 5);
        
        appRO = waitForRebindSequenceNumber(n1, n2, app, 4);
        assertEquals(n2.mgmt.getEntityManager().getEntities().size(), 1);
        assertEquals(appRO.getDisplayName(), "First App");
        assertEquals(appRO.getConfig(TestEntity.CONF_NAME), "first-app-restored");
    }


    @Test(groups="Integration", invocationCount=50)
    public void testHotStandbySeesAdditionAndRemovalManyTimes() throws Exception {
        testHotStandbySeesStructuralChangesIncludingRemoval();
    }
    
    @Test(groups="Integration") // due to time (could speed up by forcing persist and rebind)
    public void testHotStandbySeesStructuralChangesIncludingRemoval() throws Exception {
        HaMgmtNode n1 = createMaster();
        TestApplication app = createFirstAppAndPersist(n1);
        HaMgmtNode n2 = createHotStandby();

        assertEquals(n2.mgmt.getApplications().size(), 1);
        Application appRO = n2.mgmt.lookup(app.getId(), Application.class);
        Assert.assertNotNull(appRO);
        assertEquals(appRO.getChildren().size(), 0);
        
        // test additions - new child, new app
        
        TestEntity child = app.addChild(EntitySpec.create(TestEntity.class).configure(TestEntity.CONF_NAME, "first-child"));
        TestApplication app2 = TestApplication.Factory.newManagedInstanceForTests(n1.mgmt);
        
        app.setAttribute(TestEntity.SEQUENCE, 4);
        appRO = waitForRebindSequenceNumber(n1, n2, app, 4);
        
        assertEquals(appRO.getChildren().size(), 1);
        Entity childRO = Iterables.getOnlyElement(appRO.getChildren());
        assertEquals(childRO.getId(), child.getId());
        assertEquals(childRO.getConfig(TestEntity.CONF_NAME), "first-child");
        
        assertEquals(n2.mgmt.getApplications().size(), 2);
        Application app2RO = n2.mgmt.lookup(app2.getId(), Application.class);
        Assert.assertNotNull(app2RO);
        assertEquals(app2RO.getConfig(TestEntity.CONF_NAME), "second-app");
        
        assertEquals(n2.mgmt.getEntityManager().getEntities().size(), 3);
        
        // now test removals
        
        Entities.unmanage(child);
        Entities.unmanage(app2);
        
        app.setAttribute(TestEntity.SEQUENCE, 5);
        appRO = waitForRebindSequenceNumber(n1, n2, app, 5);
        
        EntityTestUtils.assertAttributeEqualsEventually(appRO, TestEntity.SEQUENCE, 5);
        assertEquals(n2.mgmt.getEntityManager().getEntities().size(), 1);
        assertEquals(appRO.getChildren().size(), 0);
        assertEquals(n2.mgmt.getApplications().size(), 1);
        Assert.assertNull(n2.mgmt.lookup(app2.getId(), Application.class));
        Assert.assertNull(n2.mgmt.lookup(child.getId(), Application.class));
    }
    
}
