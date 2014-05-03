package brooklyn.management.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.BrooklynVersion;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterInMemory;
import brooklyn.entity.rebind.plane.dto.BasicManagerMemento;
import brooklyn.management.ha.HighAvailabilityManager.StartMode;
import brooklyn.management.ha.HighAvailabilityManagerImpl;
import brooklyn.management.ha.HighAvailabilityManagerImpl.PromotionListener;
import brooklyn.management.ha.ManagementPlaneMemento;
import brooklyn.management.ha.ManagementPlaneMementoDeltaImpl;
import brooklyn.management.ha.ManagementPlaneMementoPersisterInMemory;
import brooklyn.management.ha.ManagerMemento;
import brooklyn.management.ha.ManagerMemento.HealthStatus;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.Asserts;
import brooklyn.util.time.Duration;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class HighAvailabilityManagerTest {

    private ManagementPlaneMementoPersisterInMemory persister;
    private ManagementContextInternal managementContext;
    private String ownNodeId;
    private HighAvailabilityManagerImpl manager;
    private Ticker ticker;
    private AtomicLong currentTime; // used to set the ticker's return value
    private RecordingPromotionListener promotionListener;
    private ClassLoader classLoader = getClass().getClassLoader();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        currentTime = new AtomicLong(System.nanoTime());
        ticker = new Ticker() {
            @Override public long read() {
                return currentTime.get();
            }
        };
        promotionListener = new RecordingPromotionListener();
        managementContext = new LocalManagementContext();
        managementContext.getRebindManager().setPersister(new BrooklynMementoPersisterInMemory(classLoader));
        ownNodeId = managementContext.getManagementNodeId();
        persister = new ManagementPlaneMementoPersisterInMemory();
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
        persister.delta(ManagementPlaneMementoDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, HealthStatus.STANDBY, currentTimeMillis()))
                .node(newManagerMemento("node1", HealthStatus.MASTER, currentTimeMillis()))
                .setMaster("node1")
                .build());
        
        manager.start(StartMode.AUTO);
        
        // Simulate passage of time; ticker used by this HA-manager so it will "correctly" publish
        // its own heartbeat with the new time; but node1's record is now out-of-date.
        incrementClock(31, TimeUnit.SECONDS);
        
        // Expect to be notified of our promotion, as the only other node
        promotionListener.assertCalledEventually();
    }

    @Test(groups="Integration") // because one second wait in succeedsContinually
    public void testDoesNotPromoteIfMasterTimeoutNotExpired() throws Exception {
        persister.delta(ManagementPlaneMementoDeltaImpl.builder()
                .node(newManagerMemento(ownNodeId, HealthStatus.STANDBY, currentTimeMillis()))
                .node(newManagerMemento("node1", HealthStatus.MASTER, currentTimeMillis()))
                .setMaster("node1")
                .build());
        
        manager.start(StartMode.AUTO);
        
        incrementClock(29, TimeUnit.SECONDS);
        
        // Expect not to be notified, as 29s < 30s timeout
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertTrue(promotionListener.callTimestamps.isEmpty(), "calls="+promotionListener.callTimestamps);
            }});
    }

    @Test
    public void testGetManagementPlaneStatus() throws Exception {
        persister.delta(ManagementPlaneMementoDeltaImpl.builder()
                .node(newManagerMemento("node1", HealthStatus.STANDBY, currentTimeMillis()))
                .build());
        
        manager.start(StartMode.MASTER);
        ManagementPlaneMemento memento = manager.getManagementPlaneStatus();
        
        // Note can assert timestamp because not "real" time; it's using our own Ticker
        assertEquals(memento.getMasterNodeId(), ownNodeId);
        assertEquals(memento.getNodes().keySet(), ImmutableSet.of(ownNodeId, "node1"));
        assertEquals(memento.getNodes().get(ownNodeId).getNodeId(), ownNodeId);
        assertEquals(memento.getNodes().get(ownNodeId).getStatus(), HealthStatus.MASTER);
        assertEquals(memento.getNodes().get(ownNodeId).getTimestampUtc(), currentTimeMillis());
        assertEquals(memento.getNodes().get("node1").getNodeId(), "node1");
        assertEquals(memento.getNodes().get("node1").getStatus(), HealthStatus.STANDBY);
        assertEquals(memento.getNodes().get("node1").getTimestampUtc(), currentTimeMillis());
    }
    
    private long currentTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(ticker.read());
    }
    
    private long incrementClock(long increment, TimeUnit unit) {
        currentTime.addAndGet(unit.toNanos(increment));
        return currentTimeMillis();
    }
    
    private ManagerMemento newManagerMemento(String nodeId, HealthStatus status, long timestamp) {
        return BasicManagerMemento.builder().brooklynVersion(BrooklynVersion.get()).nodeId(nodeId).status(status).timestampUtc(timestamp).build();
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
