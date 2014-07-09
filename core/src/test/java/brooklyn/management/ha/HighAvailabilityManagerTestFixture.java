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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.BrooklynVersion;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord;
import brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord.Builder;
import brooklyn.management.ha.HighAvailabilityManagerImpl.PromotionListener;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.time.Duration;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

@Test
public abstract class HighAvailabilityManagerTestFixture {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(HighAvailabilityManagerTestFixture.class);
    
    private ManagementPlaneSyncRecordPersister persister;
    private ManagementContextInternal managementContext;
    private String ownNodeId;
    private HighAvailabilityManagerImpl manager;
    private Ticker ticker;
    private AtomicLong currentTime; // used to set the ticker's return value
    private RecordingPromotionListener promotionListener;
    private ClassLoader classLoader = getClass().getClassLoader();
    private PersistenceObjectStore objectStore;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        currentTime = new AtomicLong(1000000000L);
        ticker = new Ticker() {
            // strictly not a ticker because returns millis UTC, but it works fine even so
            @Override public long read() {
                return currentTime.get();
            }
        };
        promotionListener = new RecordingPromotionListener();
        managementContext = newLocalManagementContext();
        ownNodeId = managementContext.getManagementNodeId();
        objectStore = newPersistenceObjectStore();
        objectStore.injectManagementContext(managementContext);
        objectStore.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
        persister = new ManagementPlaneSyncRecordPersisterToObjectStore(managementContext, objectStore, classLoader);
        ((ManagementPlaneSyncRecordPersisterToObjectStore)persister).allowRemoteTimestampInMemento();
        BrooklynMementoPersisterToObjectStore persisterObj = new BrooklynMementoPersisterToObjectStore(
                objectStore, 
                managementContext.getBrooklynProperties(), 
                classLoader);
        managementContext.getRebindManager().setPersister(persisterObj, PersistenceExceptionHandlerImpl.builder().build());
        manager = new HighAvailabilityManagerImpl(managementContext)
                .setPollPeriod(getPollPeriod())
                .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
                .setPromotionListener(promotionListener)
                .setLocalTicker(ticker)
                .setRemoteTicker(getRemoteTicker())
                .setPersister(persister);
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
            .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY))
            .build());

    }

    protected ManagementContextInternal newLocalManagementContext() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED, false);
        return new LocalManagementContextForTests(props);
    }

    protected abstract PersistenceObjectStore newPersistenceObjectStore();
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (manager != null) manager.stop();
        if (managementContext != null) Entities.destroyAll(managementContext);
        if (objectStore != null) objectStore.deleteCompletely();
    }
    
    // The web-console could still be polling (e.g. if have just restarted brooklyn), before the persister is set.
    // Must not throw NPE, but instead return something sensible (e.g. an empty state record).
    @Test
    public void testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet() throws Exception {
        HighAvailabilityManagerImpl manager2 = new HighAvailabilityManagerImpl(managementContext)
            .setPollPeriod(Duration.millis(10))
            .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
            .setPromotionListener(promotionListener)
            .setLocalTicker(ticker)
            .setRemoteTicker(ticker);
        try {
            ManagementPlaneSyncRecord state = manager2.getManagementPlaneSyncState();
            assertNotNull(state);
        } finally {
            manager2.stop();
        }

    }
    // Can get a log.error about our management node's heartbeat being out of date. Caused by
    // poller first writing a heartbeat record, and then the clock being incremented. But the
    // next poll fixes it.
    public void testPromotes() throws Exception {
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY))
                .node(newManagerMemento("node1", ManagementNodeState.MASTER))
                .setMaster("node1")
                .build());
        
        manager.start(HighAvailabilityMode.AUTO);
        
        // Simulate passage of time; ticker used by this HA-manager so it will "correctly" publish
        // its own heartbeat with the new time; but node1's record is now out-of-date.
        tickerAdvance(Duration.seconds(31));
        
        // Expect to be notified of our promotion, as the only other node
        promotionListener.assertCalledEventually();
    }

    @Test(groups="Integration") // because one second wait in succeedsContinually
    public void testDoesNotPromoteIfMasterTimeoutNotExpired() throws Exception {
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY))
                .node(newManagerMemento("node1", ManagementNodeState.MASTER))
                .setMaster("node1")
                .build());
        
        manager.start(HighAvailabilityMode.AUTO);
        
        tickerAdvance(Duration.seconds(25));
        
        // Expect not to be notified, as 25s < 30s timeout
        // (it's normally a fake clock so won't hit 30, even waiting 1s below - but in "IntegrationTest" subclasses it is real!)
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertTrue(promotionListener.callTimestamps.isEmpty(), "calls="+promotionListener.callTimestamps);
            }});
    }

    public void testGetManagementPlaneStatus() throws Exception {
        // with the name zzzzz the mgr created here should never be promoted by the alphabetical strategy!
        
        tickerAdvance(Duration.FIVE_SECONDS);
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY))
                .node(newManagerMemento("zzzzzzz_node1", ManagementNodeState.STANDBY))
                .build());
        persister.loadSyncRecord();
        long zzzTime = tickerCurrentMillis();
        tickerAdvance(Duration.FIVE_SECONDS);
        
        manager.start(HighAvailabilityMode.AUTO);
        ManagementPlaneSyncRecord memento = manager.getManagementPlaneSyncState();
        
        // Note can assert timestamp because not "real" time; it's using our own Ticker
        assertEquals(memento.getMasterNodeId(), ownNodeId);
        assertEquals(memento.getManagementNodes().keySet(), ImmutableSet.of(ownNodeId, "zzzzzzz_node1"));
        assertEquals(memento.getManagementNodes().get(ownNodeId).getNodeId(), ownNodeId);
        assertEquals(memento.getManagementNodes().get(ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento.getManagementNodes().get(ownNodeId).getLocalTimestamp(), tickerCurrentMillis());
        assertEquals(memento.getManagementNodes().get("zzzzzzz_node1").getNodeId(), "zzzzzzz_node1");
        assertEquals(memento.getManagementNodes().get("zzzzzzz_node1").getStatus(), ManagementNodeState.STANDBY);
        assertEquals(memento.getManagementNodes().get("zzzzzzz_node1").getLocalTimestamp(), zzzTime);
    }

    @Test(groups="Integration", invocationCount=50) //because we have had non-deterministic failures 
    public void testGetManagementPlaneStatusManyTimes() throws Exception {
        testGetManagementPlaneStatus();
    }
    
    @Test
    public void testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed() throws Exception {
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY))
                .node(newManagerMemento("node1", ManagementNodeState.MASTER))
                .setMaster("node1")
                .build());
        
        manager.start(HighAvailabilityMode.AUTO);
        
        ManagementPlaneSyncRecord state = manager.getManagementPlaneSyncState();
        assertEquals(state.getManagementNodes().get("node1").getStatus(), ManagementNodeState.MASTER);
        assertEquals(state.getManagementNodes().get(ownNodeId).getStatus(), ManagementNodeState.STANDBY);
        
        // Simulate passage of time; ticker used by this HA-manager so it will "correctly" publish
        // its own heartbeat with the new time; but node1's record is now out-of-date.
        tickerAdvance(Duration.seconds(31));
        
        ManagementPlaneSyncRecord state2 = manager.getManagementPlaneSyncState();
        assertEquals(state2.getManagementNodes().get("node1").getStatus(), ManagementNodeState.FAILED);
        assertNotEquals(state.getManagementNodes().get(ownNodeId).getStatus(), ManagementNodeState.FAILED);
    }

    protected Duration getPollPeriod() {
        return Duration.millis(10);
    }
    
    protected long tickerCurrentMillis() {
        return ticker.read();
    }
    
    protected long tickerAdvance(Duration duration) {
        currentTime.addAndGet(duration.toMilliseconds());
        return tickerCurrentMillis();
    }

    protected Ticker getRemoteTicker() {
        return ticker;
    }
    
    protected ManagementNodeSyncRecord newManagerMemento(String nodeId, ManagementNodeState status) {
        Builder rb = BasicManagementNodeSyncRecord.builder();
        rb.brooklynVersion(BrooklynVersion.get()).nodeId(nodeId).status(status);
        rb.localTimestamp(tickerCurrentMillis());
        if (getRemoteTicker()!=null)
            rb.remoteTimestamp(getRemoteTicker().read());
        return rb.build();
    }
    
    public static class RecordingPromotionListener implements PromotionListener {
        public final List<Long> callTimestamps = Lists.newCopyOnWriteArrayList();
        
        @Override
        public void promotingToMaster() {
            callTimestamps.add(System.currentTimeMillis());
        }
        
        public void assertNotCalled() {
            assertTrue(callTimestamps.isEmpty(), "calls="+callTimestamps);
        }

        public void assertCalled() {
            assertFalse(callTimestamps.isEmpty(), "calls="+callTimestamps);
        }

        public void assertCalledEventually() {
            Asserts.succeedsEventually(new Runnable() {
                @Override public void run() {
                    assertCalled();
                }});
        }
    };
}
