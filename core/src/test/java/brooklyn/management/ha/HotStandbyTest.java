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

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
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
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.ListeningObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.ByteSizeStrings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.Iterables;

public class HotStandbyTest {

    private static final Logger log = LoggerFactory.getLogger(HotStandbyTest.class);
    
    private List<HaMgmtNode> nodes = new MutableList<HotStandbyTest.HaMgmtNode>();
    Map<String,String> sharedBackingStore = MutableMap.of();
    Map<String,Date> sharedBackingStoreDates = MutableMap.of();
    private ClassLoader classLoader = getClass().getClassLoader();
    
    public class HaMgmtNode {
        // TODO share with WarmStandbyTest and SplitBrainTest and a few others (minor differences but worth it ultimately)

        private ManagementContextInternal mgmt;
        private String ownNodeId;
        private String nodeName;
        private ListeningObjectStore objectStore;
        private ManagementPlaneSyncRecordPersister persister;
        private HighAvailabilityManagerImpl ha;
        private Duration persistOrRebindPeriod = Duration.ONE_SECOND;

        public void setUp() throws Exception {
            nodeName = "node "+nodes.size();
            mgmt = newLocalManagementContext();
            ownNodeId = mgmt.getManagementNodeId();
            objectStore = new ListeningObjectStore(newPersistenceObjectStore());
            objectStore.injectManagementContext(mgmt);
            objectStore.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
            persister = new ManagementPlaneSyncRecordPersisterToObjectStore(mgmt, objectStore, classLoader);
            ((ManagementPlaneSyncRecordPersisterToObjectStore)persister).preferRemoteTimestampInMemento();
            BrooklynMementoPersisterToObjectStore persisterObj = new BrooklynMementoPersisterToObjectStore(objectStore, mgmt.getBrooklynProperties(), classLoader);
            ((RebindManagerImpl)mgmt.getRebindManager()).setPeriodicPersistPeriod(persistOrRebindPeriod);
            mgmt.getRebindManager().setPersister(persisterObj, PersistenceExceptionHandlerImpl.builder().build());
            ha = ((HighAvailabilityManagerImpl)mgmt.getHighAvailabilityManager())
                .setPollPeriod(Duration.PRACTICALLY_FOREVER)
                .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
                .setPersister(persister);
            log.info("Created "+nodeName+" "+ownNodeId);
        }
        
        public void tearDownThisOnly() throws Exception {
            if (ha != null) ha.stop();
            if (mgmt!=null) mgmt.getRebindManager().stop();
            if (mgmt != null) Entities.destroyAll(mgmt);
        }
        
        public void tearDownAll() throws Exception {
            tearDownThisOnly();
            // can't delete the object store until all being torn down
            if (objectStore != null) objectStore.deleteCompletely();
        }
        
        @Override
        public String toString() {
            return nodeName+" "+ownNodeId;
        }

        public RebindManagerImpl rebinder() {
            return (RebindManagerImpl)mgmt.getRebindManager();
        }
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        nodes.clear();
        sharedBackingStore.clear();
    }
    
    public HaMgmtNode newNode(Duration persistOrRebindPeriod) throws Exception {
        HaMgmtNode node = new HaMgmtNode();
        node.persistOrRebindPeriod = persistOrRebindPeriod;
        node.setUp();
        nodes.add(node);
        return node;
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (HaMgmtNode n: nodes)
            n.tearDownAll();
    }

    protected ManagementContextInternal newLocalManagementContext() {
        return new LocalManagementContextForTests();
    }

    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore(sharedBackingStore, sharedBackingStoreDates);
    }

    private HaMgmtNode createMaster(Duration persistOrRebindPeriod) throws Exception {
        HaMgmtNode n1 = newNode(persistOrRebindPeriod);
        n1.ha.start(HighAvailabilityMode.AUTO);
        assertEquals(n1.ha.getNodeState(), ManagementNodeState.MASTER);
        return n1;
    }
    
    private HaMgmtNode createHotStandby(Duration rebindPeriod) throws Exception {
        HaMgmtNode n2 = newNode(rebindPeriod);
        n2.ha.start(HighAvailabilityMode.HOT_STANDBY);
        assertEquals(n2.ha.getNodeState(), ManagementNodeState.HOT_STANDBY);
        return n2;
    }

    private TestApplication createFirstAppAndPersist(HaMgmtNode n1) throws Exception {
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(n1.mgmt);
        // for testing without enrichers, if desired:
//        TestApplication app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).impl(TestApplicationNoEnrichersImpl.class), n1.mgmt);
        app.setDisplayName("First App");
        app.start(MutableList.<Location>of());
        app.setConfig(TestEntity.CONF_NAME, "first-app");
        app.setAttribute(TestEntity.SEQUENCE, 3);
        
        forcePersistNow(n1);
        return app;
    }

    protected void forcePersistNow(HaMgmtNode n1) {
        n1.mgmt.getRebindManager().forcePersistNow();
    }
    
    private Application expectRebindSequenceNumber(HaMgmtNode master, HaMgmtNode hotStandby, Application app, int expectedSensorSequenceValue, boolean immediate) {
        Application appRO = hotStandby.mgmt.lookup(app.getId(), Application.class);

        if (immediate) {
            forcePersistNow(master);
            forceRebindNow(hotStandby);
            EntityTestUtils.assertAttributeEquals(appRO, TestEntity.SEQUENCE, expectedSensorSequenceValue);
        } else {
            EntityTestUtils.assertAttributeEqualsEventually(appRO, TestEntity.SEQUENCE, expectedSensorSequenceValue);
        }
        
        log.info("got sequence number "+expectedSensorSequenceValue+" from "+appRO);
        
        // make sure the instance (proxy) is unchanged
        Application appRO2 = hotStandby.mgmt.lookup(app.getId(), Application.class);
        Assert.assertTrue(appRO2==appRO);
        
        return appRO;
    }

    private void forceRebindNow(HaMgmtNode hotStandby) {
        hotStandby.mgmt.getRebindManager().rebind(null, null, ManagementNodeState.HOT_STANDBY);
    }
    
    @Test
    public void testHotStandbySeesInitialCustomNameConfigAndSensorValueButDoesntAllowChange() throws Exception {
        HaMgmtNode n1 = createMaster(Duration.PRACTICALLY_FOREVER);
        TestApplication app = createFirstAppAndPersist(n1);
        HaMgmtNode n2 = createHotStandby(Duration.PRACTICALLY_FOREVER);

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
        assertEquals(appRO.getAttribute(TestEntity.SEQUENCE), (Integer)3);
    }

    @Test
    public void testHotStandbySeesChangesToNameConfigAndSensorValue() throws Exception {
        HaMgmtNode n1 = createMaster(Duration.PRACTICALLY_FOREVER);
        TestApplication app = createFirstAppAndPersist(n1);
        HaMgmtNode n2 = createHotStandby(Duration.PRACTICALLY_FOREVER);

        assertEquals(n2.mgmt.getApplications().size(), 1);
        Application appRO = n2.mgmt.lookup(app.getId(), Application.class);
        Assert.assertNotNull(appRO);
        assertEquals(appRO.getChildren().size(), 0);
        
        // test changes

        app.setDisplayName("First App Renamed");
        app.setConfig(TestEntity.CONF_NAME, "first-app-renamed");
        app.setAttribute(TestEntity.SEQUENCE, 4);

        appRO = expectRebindSequenceNumber(n1, n2, app, 4, true);
        assertEquals(n2.mgmt.getEntityManager().getEntities().size(), 1);
        assertEquals(appRO.getDisplayName(), "First App Renamed");
        assertEquals(appRO.getConfig(TestEntity.CONF_NAME), "first-app-renamed");
        
        // and change again for good measure!

        app.setDisplayName("First App");
        app.setConfig(TestEntity.CONF_NAME, "first-app-restored");
        app.setAttribute(TestEntity.SEQUENCE, 5);
        
        appRO = expectRebindSequenceNumber(n1, n2, app, 5, true);
        assertEquals(n2.mgmt.getEntityManager().getEntities().size(), 1);
        assertEquals(appRO.getDisplayName(), "First App");
        assertEquals(appRO.getConfig(TestEntity.CONF_NAME), "first-app-restored");
    }


    public void testHotStandbySeesStructuralChangesIncludingRemoval() throws Exception {
        doTestHotStandbySeesStructuralChangesIncludingRemoval(true);
    }
    
    @Test(groups="Integration") // due to time (it waits for background persistence)
    public void testHotStandbyUnforcedSeesStructuralChangesIncludingRemoval() throws Exception {
        doTestHotStandbySeesStructuralChangesIncludingRemoval(false);
    }
    
    public void doTestHotStandbySeesStructuralChangesIncludingRemoval(boolean immediate) throws Exception {
        HaMgmtNode n1 = createMaster(immediate ? Duration.PRACTICALLY_FOREVER : Duration.millis(200));
        TestApplication app = createFirstAppAndPersist(n1);
        HaMgmtNode n2 = createHotStandby(immediate ? Duration.PRACTICALLY_FOREVER : Duration.millis(200));

        assertEquals(n2.mgmt.getApplications().size(), 1);
        Application appRO = n2.mgmt.lookup(app.getId(), Application.class);
        Assert.assertNotNull(appRO);
        assertEquals(appRO.getChildren().size(), 0);
        
        // test additions - new child, new app
        
        TestEntity child = app.addChild(EntitySpec.create(TestEntity.class).configure(TestEntity.CONF_NAME, "first-child"));
        Entities.manage(child);
        TestApplication app2 = TestApplication.Factory.newManagedInstanceForTests(n1.mgmt);
        app2.setConfig(TestEntity.CONF_NAME, "second-app");
        
        app.setAttribute(TestEntity.SEQUENCE, 4);
        appRO = expectRebindSequenceNumber(n1, n2, app, 4, immediate);
        
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
        appRO = expectRebindSequenceNumber(n1, n2, app, 5, immediate);
        
        EntityTestUtils.assertAttributeEqualsEventually(appRO, TestEntity.SEQUENCE, 5);
        assertEquals(n2.mgmt.getEntityManager().getEntities().size(), 1);
        assertEquals(appRO.getChildren().size(), 0);
        assertEquals(n2.mgmt.getApplications().size(), 1);
        Assert.assertNull(n2.mgmt.lookup(app2.getId(), Application.class));
        Assert.assertNull(n2.mgmt.lookup(child.getId(), Application.class));
    }

    @Test(groups="Integration", invocationCount=50)
    public void testHotStandbySeesStructuralChangesIncludingRemovalManyTimes() throws Exception {
        doTestHotStandbySeesStructuralChangesIncludingRemoval(true);
    }

    Deque<Long> usedMemory = new ArrayDeque<Long>();
    protected long noteUsedMemory(String message) {
        Time.sleep(Duration.millis(200));
        for (HaMgmtNode n: nodes) {
            ((AbstractManagementContext)n.mgmt).getGarbageCollector().gcIteration();
        }
        System.gc(); System.gc();
        Time.sleep(Duration.millis(50));
        System.gc(); System.gc();
        long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        usedMemory.addLast(mem);
        log.info("Memory used - "+message+": "+ByteSizeStrings.java().apply(mem));
        return mem;
    }
    public void assertUsedMemoryLessThan(String event, long max) {
        noteUsedMemory(event);
        long nowUsed = usedMemory.peekLast();
        if (nowUsed > max) {
            // aggressively try to force GC
            Time.sleep(Duration.ONE_SECOND);
            usedMemory.removeLast();
            noteUsedMemory(event+" (extra GC)");
            nowUsed = usedMemory.peekLast();
            if (nowUsed > max) {
                Assert.fail("Too much memory used - "+ByteSizeStrings.java().apply(nowUsed)+" > max "+ByteSizeStrings.java().apply(max));
            }
        }
    }
    public void assertUsedMemoryMaxDelta(String event, long deltaMegabytes) {
        assertUsedMemoryLessThan(event, usedMemory.peekLast() + deltaMegabytes*1024*1024);
    }

    @Test(groups="Integration")
    public void testHotStandbyDoesNotLeakLotsOfRebinds() throws Exception {
        log.info("Starting test "+JavaClassNames.niceClassAndMethod());
        final int DELTA = 2;
        
        HaMgmtNode n1 = createMaster(Duration.PRACTICALLY_FOREVER);
        TestApplication app = createFirstAppAndPersist(n1);
        long initialUsed = noteUsedMemory("Created app");
        
        HaMgmtNode n2 = createHotStandby(Duration.PRACTICALLY_FOREVER);
        assertUsedMemoryMaxDelta("Standby created", DELTA);
        
        forcePersistNow(n1);
        forceRebindNow(n2);
        assertUsedMemoryMaxDelta("Persisted and rebinded once", DELTA);
        
        for (int i=0; i<10; i++) {
            forcePersistNow(n1);
            forceRebindNow(n2);
        }
        assertUsedMemoryMaxDelta("Persisted and rebinded 10x", DELTA);
        
        for (int i=0; i<1000; i++) {
            if ((i+1)%100==0) {
                noteUsedMemory("iteration "+(i+1));
                usedMemory.removeLast();
            }
            forcePersistNow(n1);
            forceRebindNow(n2);
        }
        assertUsedMemoryMaxDelta("Persisted and rebinded 1000x", DELTA);
        
        Entities.unmanage(app);
        forcePersistNow(n1);
        forceRebindNow(n2);
        
        assertUsedMemoryLessThan("And now all unmanaged", initialUsed + DELTA*1000*1000);
    }

    static class BigObject {
        public BigObject(int sizeBytes) { array = new byte[sizeBytes]; }
        byte[] array;
    }
    
    @Test(groups="Integration")
    public void testHotStandbyDoesNotLeakBigObjects() throws Exception {
        log.info("Starting test "+JavaClassNames.niceClassAndMethod());
        final int SIZE = 5;
        final int SIZE_UP_BOUND = SIZE+2;
        final int SIZE_DOWN_BOUND = SIZE-1;
        final int GRACE = 2;
        // the XML persistence uses a lot of space, we approx at between 2x and 3c
        final int SIZE_IN_XML = 3*SIZE;
        final int SIZE_IN_XML_DOWN = 2*SIZE;
        
        HaMgmtNode n1 = createMaster(Duration.PRACTICALLY_FOREVER);
        TestApplication app = createFirstAppAndPersist(n1);        
        noteUsedMemory("Finished seeding");
        Long initialUsed = usedMemory.peekLast();
        app.setConfig(TestEntity.CONF_OBJECT, new BigObject(SIZE*1000*1000));
        assertUsedMemoryMaxDelta("Set a big config object", SIZE_UP_BOUND);
        forcePersistNow(n1);
        assertUsedMemoryMaxDelta("Persisted a big config object", SIZE_IN_XML);
        
        HaMgmtNode n2 = createHotStandby(Duration.PRACTICALLY_FOREVER);
        forceRebindNow(n2);
        assertUsedMemoryMaxDelta("Rebinded", SIZE_UP_BOUND);
        
        for (int i=0; i<10; i++)
            forceRebindNow(n2);
        assertUsedMemoryMaxDelta("Several more rebinds", GRACE);
        for (int i=0; i<10; i++) {
            forcePersistNow(n1);
            forceRebindNow(n2);
        }
        assertUsedMemoryMaxDelta("And more rebinds and more persists", GRACE);
        
        app.setConfig(TestEntity.CONF_OBJECT, "big is now small");
        assertUsedMemoryMaxDelta("Big made small at primary", -SIZE_DOWN_BOUND);
        forcePersistNow(n1);
        assertUsedMemoryMaxDelta("And persisted", -SIZE_IN_XML_DOWN);
        
        forceRebindNow(n2);
        assertUsedMemoryMaxDelta("And at secondary", -SIZE_DOWN_BOUND);
        
        Entities.unmanage(app);
        forcePersistNow(n1);
        forceRebindNow(n2);
        
        assertUsedMemoryLessThan("And now all unmanaged", initialUsed+GRACE*1000*1000);
    }

    @Test(groups="Integration") // because it's slow
    // Sept 2014 - there is a small leak, of 200 bytes per child created and destroyed;
    // but this goes away when the app is destroyed; it may be a benign record
    public void testHotStandbyDoesNotLeakLotsOfRebindsCreatingAndDestroyingAChildEntity() throws Exception {
        log.info("Starting test "+JavaClassNames.niceClassAndMethod());
        final int DELTA = 2;
        
        HaMgmtNode n1 = createMaster(Duration.PRACTICALLY_FOREVER);
        TestApplication app = createFirstAppAndPersist(n1);
        long initialUsed = noteUsedMemory("Created app");
        
        HaMgmtNode n2 = createHotStandby(Duration.PRACTICALLY_FOREVER);
        assertUsedMemoryMaxDelta("Standby created", DELTA);
        
        TestEntity lastChild = app.addChild(EntitySpec.create(TestEntity.class).configure(TestEntity.CONF_NAME, "first-child"));
        Entities.manage(lastChild);
        forcePersistNow(n1);
        forceRebindNow(n2);
        assertUsedMemoryMaxDelta("Child created and rebinded once", DELTA);
        
        for (int i=0; i<1000; i++) {
            if (i==9 || (i+1)%100==0) {
                noteUsedMemory("iteration "+(i+1));
                usedMemory.removeLast();
            }
            TestEntity newChild = app.addChild(EntitySpec.create(TestEntity.class).configure(TestEntity.CONF_NAME, "first-child"));
            Entities.manage(newChild);
            Entities.unmanage(lastChild);
            lastChild = newChild;
            
            forcePersistNow(n1);
            forceRebindNow(n2);
        }
        assertUsedMemoryMaxDelta("Persisted and rebinded 1000x", DELTA);
        
        Entities.unmanage(app);
        forcePersistNow(n1);
        forceRebindNow(n2);
        
        assertUsedMemoryLessThan("And now all unmanaged", initialUsed + DELTA*1000*1000);
    }
    
    protected void assertHotStandby(HaMgmtNode n1) {
        assertEquals(n1.ha.getNodeState(), ManagementNodeState.HOT_STANDBY);
        Assert.assertTrue(n1.rebinder().isReadOnlyRunning());
        Assert.assertFalse(n1.rebinder().isPersistenceRunning());
    }

    protected void assertMaster(HaMgmtNode n1) {
        assertEquals(n1.ha.getNodeState(), ManagementNodeState.MASTER);
        Assert.assertFalse(n1.rebinder().isReadOnlyRunning());
        Assert.assertTrue(n1.rebinder().isPersistenceRunning());
    }

    @Test
    public void testChangeMode() throws Exception {
        HaMgmtNode n1 = createMaster(Duration.PRACTICALLY_FOREVER);
        TestApplication app = createFirstAppAndPersist(n1);
        HaMgmtNode n2 = createHotStandby(Duration.PRACTICALLY_FOREVER);

        TestEntity child = app.addChild(EntitySpec.create(TestEntity.class).configure(TestEntity.CONF_NAME, "first-child"));
        Entities.manage(child);
        TestApplication app2 = TestApplication.Factory.newManagedInstanceForTests(n1.mgmt);
        app2.setConfig(TestEntity.CONF_NAME, "second-app");

        forcePersistNow(n1);
        n2.ha.setPriority(1);
        n1.ha.changeMode(HighAvailabilityMode.HOT_STANDBY);
        
        // both now hot standby
        assertHotStandby(n1);
        assertHotStandby(n2);
        
        assertEquals(n1.mgmt.getApplications().size(), 2);
        Application app2RO = n1.mgmt.lookup(app2.getId(), Application.class);
        Assert.assertNotNull(app2RO);
        assertEquals(app2RO.getConfig(TestEntity.CONF_NAME), "second-app");
        try {
            ((TestApplication)app2RO).setAttribute(TestEntity.SEQUENCE, 4);
            Assert.fail("Should not have allowed sensor to be set");
        } catch (Exception e) {
            Assert.assertTrue(e.toString().toLowerCase().contains("read-only"), "Error message did not contain expected text: "+e);
        }

        n1.ha.changeMode(HighAvailabilityMode.AUTO);
        n2.ha.changeMode(HighAvailabilityMode.HOT_STANDBY, true, false);
        // both still hot standby (n1 will defer to n2 as it has higher priority)
        assertHotStandby(n1);
        assertHotStandby(n2);
        
        // with priority 1, n2 will now be elected
        n2.ha.changeMode(HighAvailabilityMode.AUTO);
        assertHotStandby(n1);
        assertMaster(n2);
        
        assertEquals(n2.mgmt.getApplications().size(), 2);
        Application app2B = n2.mgmt.lookup(app2.getId(), Application.class);
        Assert.assertNotNull(app2B);
        assertEquals(app2B.getConfig(TestEntity.CONF_NAME), "second-app");
        ((TestApplication)app2B).setAttribute(TestEntity.SEQUENCE, 4);
        
        forcePersistNow(n2);
        forceRebindNow(n1);
        Application app2BRO = n1.mgmt.lookup(app2.getId(), Application.class);
        Assert.assertNotNull(app2BRO);
        EntityTestUtils.assertAttributeEquals(app2BRO, TestEntity.SEQUENCE, 4);
    }

    @Test(groups="Integration", invocationCount=20)
    public void testChangeModeManyTimes() throws Exception {
        testChangeMode();
    }

    @Test
    public void testChangeModeToDisabledAndBack() throws Exception {
        HaMgmtNode n1 = createMaster(Duration.PRACTICALLY_FOREVER);
        n1.mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachine.class));
        @SuppressWarnings("unused")
        TestApplication app = createFirstAppAndPersist(n1);
        
        HaMgmtNode n2 = createHotStandby(Duration.PRACTICALLY_FOREVER);
        
        // disabled n1 allows n2 to become master when next we tell it to check
        n1.ha.changeMode(HighAvailabilityMode.DISABLED);
        n2.ha.changeMode(HighAvailabilityMode.AUTO);
        assertMaster(n2);
        assertEquals(n1.ha.getNodeState(), ManagementNodeState.FAILED);
        Assert.assertTrue(n1.mgmt.getApplications().isEmpty(), "n1 should have had no apps; instead had: "+n1.mgmt.getApplications());
        Assert.assertTrue(n1.mgmt.getEntityManager().getEntities().isEmpty(), "n1 should have had no entities; instead had: "+n1.mgmt.getEntityManager().getEntities());
        Assert.assertTrue(n1.mgmt.getLocationManager().getLocations().isEmpty(), "n1 should have had no locations; instead had: "+n1.mgmt.getLocationManager().getLocations());
        
        // we can now change n1 back to hot_standby
        n1.ha.changeMode(HighAvailabilityMode.HOT_STANDBY);
        assertHotStandby(n1);
        // and it sees apps
        Assert.assertFalse(n1.mgmt.getApplications().isEmpty(), "n1 should have had apps now");
        Assert.assertFalse(n1.mgmt.getLocationManager().getLocations().isEmpty(), "n1 should have had locations now");
        // and if n2 is disabled, n1 promotes
        n2.ha.changeMode(HighAvailabilityMode.DISABLED);
        n1.ha.changeMode(HighAvailabilityMode.AUTO);
        assertMaster(n1);
        assertEquals(n2.ha.getNodeState(), ManagementNodeState.FAILED);
    }
    
}
