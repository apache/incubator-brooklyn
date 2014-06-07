package brooklyn.management.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.BrooklynVersion;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterInMemory;
import brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord;
import brooklyn.management.ha.HighAvailabilityManagerImpl.PromotionListener;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.Asserts;
import brooklyn.util.time.Duration;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class HighAvailabilityManagerTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(HighAvailabilityManagerTest.class);
    
    private ManagementPlaneSyncRecordPersisterInMemory persister;
    private ManagementContextInternal managementContext;
    private String ownNodeId;
    private HighAvailabilityManagerImpl manager;
    private Ticker ticker;
    private AtomicLong currentTime; // used to set the ticker's return value
    private RecordingPromotionListener promotionListener;
    private ClassLoader classLoader = getClass().getClassLoader();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        currentTime = new AtomicLong(System.currentTimeMillis());
        ticker = new Ticker() {
            @Override public long read() {
                return currentTime.get();
            }
        };
        promotionListener = new RecordingPromotionListener();
        managementContext = new LocalManagementContext();
        managementContext.getRebindManager().setPersister(new BrooklynMementoPersisterInMemory(classLoader));
        ownNodeId = managementContext.getManagementNodeId();
        persister = new ManagementPlaneSyncRecordPersisterInMemory();
        manager = new HighAvailabilityManagerImpl(managementContext)
                .setPollPeriod(Duration.of(10, TimeUnit.MILLISECONDS))
                .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
                .setPromotionListener(promotionListener)
                .setTicker(ticker)
                .setPersister(persister);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (manager != null) manager.stop();
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    // Can get a log.error about our management node's heartbeat being out of date. Caused by
    // poller first writing a heartbeat record, and then the clock being incremented. But the
    // next poll fixes it.
    @Test
    public void testPromotes() throws Exception {
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY, currentTimeMillis()))
                .node(newManagerMemento("node1", ManagementNodeState.MASTER, currentTimeMillis()))
                .setMaster("node1")
                .build());
        
        manager.start(HighAvailabilityMode.AUTO);
        
        // Simulate passage of time; ticker used by this HA-manager so it will "correctly" publish
        // its own heartbeat with the new time; but node1's record is now out-of-date.
        incrementClock(31, TimeUnit.SECONDS);
        
        // Expect to be notified of our promotion, as the only other node
        promotionListener.assertCalledEventually();
    }

    @Test(groups="Integration") // because one second wait in succeedsContinually
    public void testDoesNotPromoteIfMasterTimeoutNotExpired() throws Exception {
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY, currentTimeMillis()))
                .node(newManagerMemento("node1", ManagementNodeState.MASTER, currentTimeMillis()))
                .setMaster("node1")
                .build());
        
        manager.start(HighAvailabilityMode.AUTO);
        
        incrementClock(29, TimeUnit.SECONDS);
        
        // Expect not to be notified, as 29s < 30s timeout (it's a fake clock so won't hit 30, even waiting 1s below)
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertTrue(promotionListener.callTimestamps.isEmpty(), "calls="+promotionListener.callTimestamps);
            }});
    }

    @Test
    public void testGetManagementPlaneStatus() throws Exception {
        // with the name zzzzz this should never get chosen by the alphabetical strategy!
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento("zzzzzzz_node1", ManagementNodeState.STANDBY, currentTimeMillis()))
                .build());
        
        manager.start(HighAvailabilityMode.AUTO);
        ManagementPlaneSyncRecord memento = manager.getManagementPlaneSyncState();
        
        // Note can assert timestamp because not "real" time; it's using our own Ticker
        assertEquals(memento.getMasterNodeId(), ownNodeId);
        assertEquals(memento.getManagementNodes().keySet(), ImmutableSet.of(ownNodeId, "zzzzzzz_node1"));
        assertEquals(memento.getManagementNodes().get(ownNodeId).getNodeId(), ownNodeId);
        assertEquals(memento.getManagementNodes().get(ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento.getManagementNodes().get(ownNodeId).getTimestampUtc(), currentTimeMillis());
        assertEquals(memento.getManagementNodes().get("zzzzzzz_node1").getNodeId(), "zzzzzzz_node1");
        assertEquals(memento.getManagementNodes().get("zzzzzzz_node1").getStatus(), ManagementNodeState.STANDBY);
        assertEquals(memento.getManagementNodes().get("zzzzzzz_node1").getTimestampUtc(), currentTimeMillis());
    }

    @Test(groups="Integration", invocationCount=50) //because we have had non-deterministic failures 
    public void testGetManagementPlaneStatusManyTimes() throws Exception {
        testGetManagementPlaneStatus();
    }
    
    @Test
    public void testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed() throws Exception {
        persister.delta(ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, ManagementNodeState.STANDBY, currentTimeMillis()))
                .node(newManagerMemento("node1", ManagementNodeState.MASTER, currentTimeMillis()))
                .setMaster("node1")
                .build());
        
        manager.start(HighAvailabilityMode.AUTO);
        
        ManagementPlaneSyncRecord state = manager.getManagementPlaneSyncState();
        assertEquals(state.getManagementNodes().get("node1").getStatus(), ManagementNodeState.MASTER);
        assertEquals(state.getManagementNodes().get(ownNodeId).getStatus(), ManagementNodeState.STANDBY);
        
        // Simulate passage of time; ticker used by this HA-manager so it will "correctly" publish
        // its own heartbeat with the new time; but node1's record is now out-of-date.
        incrementClock(31, TimeUnit.SECONDS);
        
        ManagementPlaneSyncRecord state2 = manager.getManagementPlaneSyncState();
        assertEquals(state2.getManagementNodes().get("node1").getStatus(), ManagementNodeState.FAILED);
        assertNotEquals(state.getManagementNodes().get(ownNodeId).getStatus(), ManagementNodeState.FAILED);
    }

    private long currentTimeMillis() {
        return ticker.read();
    }
    
    private long incrementClock(long increment, TimeUnit unit) {
        currentTime.addAndGet(unit.toMillis(increment));
        return currentTimeMillis();
    }
    
    private ManagementNodeSyncRecord newManagerMemento(String nodeId, ManagementNodeState status, long timestamp) {
        return BasicManagementNodeSyncRecord.builder().brooklynVersion(BrooklynVersion.get()).nodeId(nodeId).status(status).timestampUtc(timestamp).build();
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
