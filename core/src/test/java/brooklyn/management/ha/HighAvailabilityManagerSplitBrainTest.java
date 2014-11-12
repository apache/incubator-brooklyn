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
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.ListeningObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.Location;
import brooklyn.management.ha.TestEntityFailingRebind.RebindException;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;

@Test
public class HighAvailabilityManagerSplitBrainTest {

    private static final Logger log = LoggerFactory.getLogger(HighAvailabilityManagerSplitBrainTest.class);
    
    private List<HaMgmtNode> nodes = new MutableList<HighAvailabilityManagerSplitBrainTest.HaMgmtNode>();
    Map<String,String> sharedBackingStore = MutableMap.of();
    Map<String,Date> sharedBackingStoreDates = MutableMap.of();
    private AtomicLong sharedTime; // used to set the ticker's return value
    private ClassLoader classLoader = getClass().getClassLoader();
    
    public class HaMgmtNode {
        // TODO share with HotStandbyTest and WarmStandbyTest and a few others (minor differences but worth it ultimately)

        private ManagementContextInternal mgmt;
        private String ownNodeId;
        private String nodeName;
        private ListeningObjectStore objectStore;
        private ManagementPlaneSyncRecordPersister persister;
        private HighAvailabilityManagerImpl ha;
        private Ticker ticker;
        private AtomicLong currentTime; // used to set the ticker's return value

        public void setUp() throws Exception {
            if (sharedTime==null)
                currentTime = new AtomicLong(System.currentTimeMillis());
            
            ticker = new Ticker() {
                // strictly not a ticker because returns millis UTC, but it works fine even so
                @Override public long read() {
                    if (sharedTime!=null) return sharedTime.get();
                    return currentTime.get();
                }
            };
            
            nodeName = "node "+nodes.size();
            mgmt = newLocalManagementContext();
            ownNodeId = mgmt.getManagementNodeId();
            objectStore = new ListeningObjectStore(newPersistenceObjectStore());
            objectStore.injectManagementContext(mgmt);
            objectStore.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
            persister = new ManagementPlaneSyncRecordPersisterToObjectStore(mgmt, objectStore, classLoader);
            ((ManagementPlaneSyncRecordPersisterToObjectStore)persister).preferRemoteTimestampInMemento();
            BrooklynMementoPersisterToObjectStore persisterObj = new BrooklynMementoPersisterToObjectStore(objectStore, mgmt.getBrooklynProperties(), classLoader);
            mgmt.getRebindManager().setPersister(persisterObj, PersistenceExceptionHandlerImpl.builder().build());
            ha = ((HighAvailabilityManagerImpl)mgmt.getHighAvailabilityManager())
                .setPollPeriod(Duration.PRACTICALLY_FOREVER)
                .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
                .setLocalTicker(ticker)
                .setRemoteTicker(ticker)
                .setPersister(persister);
            log.info("Created "+nodeName+" "+ownNodeId);
        }
        
        public void tearDown() throws Exception {
            if (ha != null) ha.stop();
            if (mgmt != null) Entities.destroyAll(mgmt);
            if (objectStore != null) objectStore.deleteCompletely();
        }
        
        private long tickerCurrentMillis() {
            return ticker.read();
        }
        
        private long tickerAdvance(Duration duration) {
            if (sharedTime!=null)
                throw new IllegalStateException("Using shared ticker; cannot advance private node clock");
            currentTime.addAndGet(duration.toMilliseconds());
            return tickerCurrentMillis();
        }
        
        @Override
        public String toString() {
            return nodeName+" "+ownNodeId;
        }
    }
    
    private Boolean prevThrowOnRebind;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        prevThrowOnRebind = TestEntityFailingRebind.getThrowOnRebind();
        TestEntityFailingRebind.setThrowOnRebind(true);
        nodes.clear();
        sharedBackingStore.clear();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            for (HaMgmtNode n: nodes)
                n.tearDown();
        } finally {
            if (prevThrowOnRebind != null) TestEntityFailingRebind.setThrowOnRebind(prevThrowOnRebind);
        }
    }

    public HaMgmtNode newNode() throws Exception {
        HaMgmtNode node = new HaMgmtNode();
        node.setUp();
        nodes.add(node);
        return node;
    }

    private void sharedTickerAdvance(Duration duration) {
        if (sharedTime==null) {
            for (HaMgmtNode n: nodes)
                n.tickerAdvance(duration);
        } else {
            sharedTime.addAndGet(duration.toMilliseconds());
        }
    }
    
    private long sharedTickerCurrentMillis() {
        return sharedTime.get();
    }
    
    protected void useSharedTime() {
        if (!nodes.isEmpty())
            throw new IllegalStateException("shared time must be set up before any nodes created");
        sharedTime = new AtomicLong(System.currentTimeMillis());
    }

    protected ManagementContextInternal newLocalManagementContext() {
        return new LocalManagementContextForTests();
    }

    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore(sharedBackingStore, sharedBackingStoreDates);
    }
    
    @Test
    public void testDoubleRebindFails() throws Exception {
        useSharedTime();
        HaMgmtNode n1 = newNode();
        HaMgmtNode n2 = newNode();

        // first auto should become master
        n1.ha.start(HighAvailabilityMode.AUTO);
        n2.ha.start(HighAvailabilityMode.AUTO);
        assertEquals(n1.ha.getNodeState(), ManagementNodeState.MASTER);

        TestApplication app = ApplicationBuilder.newManagedApp(
                EntitySpec.create(TestApplication.class).impl(TestEntityFailingRebind.class), n1.mgmt);
        app.start(ImmutableList.<Location>of());

        n1.mgmt.getRebindManager().forcePersistNow(false, null);

        //don't publish state for a while (i.e. long store delays, failures)
        sharedTickerAdvance(Duration.ONE_MINUTE);

        try {
            n2.ha.publishAndCheck(false);
            fail("n2 rebind failure expected");
        } catch (Exception e) {
            assertNestedRebindException(e);
        }

        try {
            n1.ha.publishAndCheck(false);
            fail("n1 rebind failure expected");
        } catch (Exception e) {
            assertNestedRebindException(e);
        }

        ManagementPlaneSyncRecord memento = n1.ha.loadManagementPlaneSyncRecord(true);
        assertEquals(memento.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.FAILED);
        assertEquals(memento.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.FAILED);
    }

    @Test
    public void testStandbyRebind() throws Exception {
        useSharedTime();
        HaMgmtNode n1 = newNode();
        HaMgmtNode n2 = newNode();

        // first auto should become master
        n1.ha.start(HighAvailabilityMode.AUTO);
        n2.ha.start(HighAvailabilityMode.AUTO);

        TestApplication app = ApplicationBuilder.newManagedApp(
                EntitySpec.create(TestApplication.class).impl(TestEntityFailingRebind.class), n1.mgmt);
        app.start(ImmutableList.<Location>of());

        n1.mgmt.getRebindManager().forcePersistNow(false, null);

        //don't publish state for a while (i.e. long store delays, failures)
        sharedTickerAdvance(Duration.ONE_MINUTE);

        try {
            n2.ha.publishAndCheck(false);
            fail("n2 rebind failure expected");
        } catch (Exception e) {
            assertNestedRebindException(e);
        }

        TestEntityFailingRebind.setThrowOnRebind(false);
        n1.ha.publishAndCheck(false);

        ManagementPlaneSyncRecord memento = n1.ha.loadManagementPlaneSyncRecord(true);
        assertEquals(memento.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.FAILED);
    }
    
    private void assertNestedRebindException(Throwable t) {
        Throwable ptr = t;
        while (ptr != null) {
            if (ptr instanceof RebindException) {
                return;
            }
            ptr = ptr.getCause();
        }
        Exceptions.propagate(t);
    }
    
    @Test
    public void testIfNodeStopsBeingAbleToWrite() throws Exception {
        useSharedTime();
        log.info("time at start "+sharedTickerCurrentMillis());
        
        HaMgmtNode n1 = newNode();
        HaMgmtNode n2 = newNode();
        
        // first auto should become master
        n1.ha.start(HighAvailabilityMode.AUTO);
        ManagementPlaneSyncRecord memento1 = n1.ha.loadManagementPlaneSyncRecord(true);
        
        log.info(n1+" HA: "+memento1);
        assertEquals(memento1.getMasterNodeId(), n1.ownNodeId);
        Long time0 = sharedTickerCurrentMillis();
        assertEquals(memento1.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time0);
        assertEquals(memento1.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.MASTER);

        // second - make explicit hot; that's a strictly more complex case than cold standby, so provides pretty good coverage
        n2.ha.start(HighAvailabilityMode.HOT_STANDBY);
        ManagementPlaneSyncRecord memento2 = n2.ha.loadManagementPlaneSyncRecord(true);
        
        log.info(n2+" HA: "+memento2);
        assertEquals(memento2.getMasterNodeId(), n1.ownNodeId);
        assertEquals(memento2.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento2.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.HOT_STANDBY);
        assertEquals(memento2.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time0);
        assertEquals(memento2.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time0);
        
        // and no entities at either
        assertEquals(n1.mgmt.getApplications().size(), 0);
        assertEquals(n2.mgmt.getApplications().size(), 0);

        // create
        TestApplication app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), n1.mgmt);
        app.start(ImmutableList.<Location>of());
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "hello");
        
        assertEquals(n1.mgmt.getApplications().size(), 1);
        assertEquals(n2.mgmt.getApplications().size(), 0);
        log.info("persisting "+n1.ownNodeId);
        n1.mgmt.getRebindManager().forcePersistNow(false, null);
        
        n1.objectStore.setWritesFailSilently(true);
        log.info(n1+" writes off");
        sharedTickerAdvance(Duration.ONE_MINUTE);
        log.info("time now "+sharedTickerCurrentMillis());
        Long time1 = sharedTickerCurrentMillis();
        
        log.info("publish "+n2.ownNodeId);
        n2.ha.publishAndCheck(false);
        ManagementPlaneSyncRecord memento2b = n2.ha.loadManagementPlaneSyncRecord(true);
        log.info(n2+" HA now: "+memento2b);
        
        // n2 infers n1 as failed 
        assertEquals(memento2b.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.FAILED);
        assertEquals(memento2b.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento2b.getMasterNodeId(), n2.ownNodeId);
        assertEquals(memento2b.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time0);
        assertEquals(memento2b.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time1);
        
        assertEquals(n1.mgmt.getApplications().size(), 1);
        assertEquals(n2.mgmt.getApplications().size(), 1);
        assertEquals(n1.mgmt.getApplications().iterator().next().getAttribute(TestApplication.MY_ATTRIBUTE), "hello");
        
        n1.objectStore.setWritesFailSilently(false);
        log.info(n1+" writes on");
        
        sharedTickerAdvance(Duration.ONE_SECOND);
        log.info("time now "+sharedTickerCurrentMillis());
        Long time2 = sharedTickerCurrentMillis();
        
        log.info("publish "+n1.ownNodeId);
        n1.ha.publishAndCheck(false);
        ManagementPlaneSyncRecord memento1b = n1.ha.getManagementPlaneSyncState();
        log.info(n1+" HA now: "+memento1b);
        
        ManagementNodeState expectedStateAfterDemotion = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY) ?
            ManagementNodeState.HOT_STANDBY : ManagementNodeState.STANDBY;
        
        // n1 comes back and demotes himself 
        assertEquals(memento1b.getManagementNodes().get(n1.ownNodeId).getStatus(), expectedStateAfterDemotion);
        assertEquals(memento1b.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento1b.getMasterNodeId(), n2.ownNodeId);
        assertEquals(memento1b.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time2);
        assertEquals(memento1b.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time1);
        
        // n2 now sees itself as master, with n1 in standby again
        ManagementPlaneSyncRecord memento2c = n2.ha.getManagementPlaneSyncState();
        log.info(n2+" HA now: "+memento2c);
        assertEquals(memento2c.getManagementNodes().get(n1.ownNodeId).getStatus(), expectedStateAfterDemotion);
        assertEquals(memento2c.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento2c.getMasterNodeId(), n2.ownNodeId);
        assertEquals(memento2c.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time2);
        assertEquals(memento2c.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time2);

        // right number of entities at n2; n1 may or may not depending whether hot standby is default
        assertEquals(n2.mgmt.getApplications().size(), 1);
        assertEquals(n1.mgmt.getApplications().size(), BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY) ? 1 : 0);
    }
    
    @Test(invocationCount=50, groups="Integration")
    public void testIfNodeStopsBeingAbleToWriteManyTimes() throws Exception {
        testIfNodeStopsBeingAbleToWrite();
    }
    
    @Test
    public void testSimultaneousStartup() throws Exception {
        doTestConcurrentStartup(5, null);
    }

    @Test
    public void testNearSimultaneousStartup() throws Exception {
        doTestConcurrentStartup(20, Duration.millis(20));
    }

    @Test(invocationCount=50, groups="Integration")
    public void testNearSimultaneousStartupManyTimes() throws Exception {
        doTestConcurrentStartup(20, Duration.millis(20));
    }

    protected void doTestConcurrentStartup(int size, final Duration staggerStart) throws Exception {
        useSharedTime();
        
        List<Thread> spawned = MutableList.of();
        for (int i=0; i<size; i++) {
            final HaMgmtNode n = newNode();
            Thread t = new Thread() { public void run() {
                if (staggerStart!=null) Time.sleep(staggerStart.multiply(Math.random()));
                n.ha.start(HighAvailabilityMode.AUTO);
                n.ha.setPollPeriod(Duration.millis(20));
            } };
            spawned.add(t);
            t.start();
        }

        try {
            final Stopwatch timer = Stopwatch.createStarted();
            Asserts.succeedsEventually(new Runnable() {
                @Override public void run() {
                    ManagementPlaneSyncRecord memento = nodes.get(0).ha.loadManagementPlaneSyncRecord(true);
                    List<ManagementNodeState> counts = MutableList.of(), savedCounts = MutableList.of();
                    for (HaMgmtNode n: nodes) {
                        counts.add(n.ha.getNodeState());
                        ManagementNodeSyncRecord m = memento.getManagementNodes().get(n.ownNodeId);
                        if (m!=null) {
                            savedCounts.add(m.getStatus());
                        }
                    }
                    log.info("while starting "+nodes.size()+" nodes: "
                        +Collections.frequency(counts, ManagementNodeState.MASTER)+" M + "
                        +Collections.frequency(counts, ManagementNodeState.HOT_STANDBY)+" hot + "
                        +Collections.frequency(counts, ManagementNodeState.STANDBY)+" warm + "
                        +Collections.frequency(counts, ManagementNodeState.INITIALIZING)+" init; "
                        + memento.getManagementNodes().size()+" saved, "
                        +Collections.frequency(savedCounts, ManagementNodeState.MASTER)+" M + "
                        +Collections.frequency(savedCounts, ManagementNodeState.HOT_STANDBY)+" hot + "
                        +Collections.frequency(savedCounts, ManagementNodeState.STANDBY)+" warm + "
                        +Collections.frequency(savedCounts, ManagementNodeState.INITIALIZING)+" init");

                    if (timer.isRunning() && Duration.of(timer).compareTo(Duration.TEN_SECONDS)>0) {
                        log.warn("we seem to have a problem stabilizing");  //handy place to set a suspend-VM breakpoint!
                        timer.stop();
                    }
                    assertEquals(Collections.frequency(counts, ManagementNodeState.MASTER), 1);
                    assertEquals(Collections.frequency(counts, ManagementNodeState.HOT_STANDBY)+Collections.frequency(counts, ManagementNodeState.STANDBY), nodes.size()-1);
                    assertEquals(Collections.frequency(savedCounts, ManagementNodeState.MASTER), 1);
                    assertEquals(Collections.frequency(savedCounts, ManagementNodeState.HOT_STANDBY)+Collections.frequency(savedCounts, ManagementNodeState.STANDBY), nodes.size()-1);
                }});
        } catch (Throwable t) {
            log.warn("Failed to stabilize (rethrowing): "+t, t);
            throw Exceptions.propagate(t);
        }
        
        for (Thread t: spawned)
            t.join(Duration.THIRTY_SECONDS.toMilliseconds());
    }
    

}
