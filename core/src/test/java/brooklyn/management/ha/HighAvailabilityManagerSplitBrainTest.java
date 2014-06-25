package brooklyn.management.ha;

import static org.testng.Assert.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.ListeningObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.management.ha.HighAvailabilityManagerTestFixture.RecordingPromotionListener;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

import com.google.common.base.Ticker;

@Test
public class HighAvailabilityManagerSplitBrainTest {

    private static final Logger log = LoggerFactory.getLogger(HighAvailabilityManagerSplitBrainTest.class);
    
    private List<HaMgmtNode> nodes = new MutableList<HighAvailabilityManagerSplitBrainTest.HaMgmtNode>();
    Map<String,String> sharedBackingStore = MutableMap.of();
    Map<String,Date> sharedBackingStoreDates = MutableMap.of();
    private AtomicLong sharedTime; // used to set the ticker's return value
    
    public class HaMgmtNode {
        
        private ManagementPlaneSyncRecordPersister persister;
        private ManagementContextInternal managementContext;
        private String ownNodeId;
        private HighAvailabilityManagerImpl manager;
        private Ticker ticker;
        private AtomicLong currentTime; // used to set the ticker's return value
        private RecordingPromotionListener promotionListener;
        private ClassLoader classLoader = getClass().getClassLoader();
        private ListeningObjectStore objectStore;
        private String nodeName;

        @BeforeMethod(alwaysRun=true)
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
            promotionListener = new RecordingPromotionListener();
            managementContext = newLocalManagementContext();
            ownNodeId = managementContext.getManagementNodeId();
            objectStore = new ListeningObjectStore(newPersistenceObjectStore());
            objectStore.injectManagementContext(managementContext);
            objectStore.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
            persister = new ManagementPlaneSyncRecordPersisterToObjectStore(managementContext, objectStore, classLoader);
            ((ManagementPlaneSyncRecordPersisterToObjectStore)persister).allowRemoteTimestampInMemento();
            BrooklynMementoPersisterToObjectStore persisterObj = new BrooklynMementoPersisterToObjectStore(objectStore, classLoader);
            managementContext.getRebindManager().setPersister(persisterObj);
            manager = new HighAvailabilityManagerImpl(managementContext)
                .setPollPeriod(Duration.PRACTICALLY_FOREVER)
                .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
                .setPromotionListener(promotionListener)
                .setLocalTicker(ticker)
                .setRemoteTicker(ticker)
                .setPersister(persister);
            log.info("Created "+nodeName+" "+ownNodeId);
        }
        
        public void tearDown() throws Exception {
            if (manager != null) manager.stop();
            if (managementContext != null) Entities.destroyAll(managementContext);
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
    public void testTwoNodes() throws Exception {
        useSharedTime();
        HaMgmtNode n1 = newNode();
        HaMgmtNode n2 = newNode();
        
        n1.manager.start(HighAvailabilityMode.AUTO);
        ManagementPlaneSyncRecord memento1 = n1.manager.getManagementPlaneSyncState();
        
        log.info(n1+" HA: "+memento1);
        assertEquals(memento1.getMasterNodeId(), n1.ownNodeId);
        Long time0 = sharedTickerCurrentMillis();
        assertEquals(memento1.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time0);
        assertEquals(memento1.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.MASTER);

        n2.manager.start(HighAvailabilityMode.AUTO);
        ManagementPlaneSyncRecord memento2 = n2.manager.getManagementPlaneSyncState();
        
        log.info(n2+" HA: "+memento2);
        assertEquals(memento2.getMasterNodeId(), n1.ownNodeId);
        assertEquals(memento2.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento2.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.STANDBY);
        assertEquals(memento2.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time0);
        assertEquals(memento2.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time0);
        
        n1.objectStore.setWritesFailSilently(true);
        log.info(n1+" writes off");
        sharedTickerAdvance(Duration.ONE_MINUTE);
        
        n2.manager.publishAndCheck(false);
        ManagementPlaneSyncRecord memento2b = n2.manager.getManagementPlaneSyncState();
        log.info(n2+" HA now: "+memento2b);
        Long time1 = sharedTickerCurrentMillis();
        
        // n2 infers n1 as failed 
        assertEquals(memento2b.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.FAILED);
        assertEquals(memento2b.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento2b.getMasterNodeId(), n2.ownNodeId);
        assertEquals(memento2b.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time0);
        assertEquals(memento2b.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time1);
        
        n1.objectStore.setWritesFailSilently(false);
        log.info(n1+" writes on");
        
        sharedTickerAdvance(Duration.ONE_SECOND);
        Long time2 = sharedTickerCurrentMillis();
        
        n1.manager.publishAndCheck(false);
        ManagementPlaneSyncRecord memento1b = n1.manager.getManagementPlaneSyncState();
        log.info(n1+" HA now: "+memento1b);
        
//        // n1 comes back and sees himself as master, but with both masters 
//        assertEquals(memento1b.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.MASTER);
//        assertEquals(memento1b.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
//        assertEquals(memento1b.getMasterNodeId(), n1.ownNodeId);
//        assertEquals(memento1b.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time2);
//        assertEquals(memento1b.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time1);
//        
//        // n2 sees itself as master, but again with both masters
//        ManagementPlaneSyncRecord memento2c = n2.manager.getManagementPlaneSyncState();
//        log.info(n2+" HA now: "+memento2c);
//        assertEquals(memento2c.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.MASTER);
//        assertEquals(memento2c.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
//        assertEquals(memento2c.getMasterNodeId(), n2.ownNodeId);
//        assertEquals(memento2c.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time2);
//        assertEquals(memento2c.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time2);

        // current (unwanted) state is above, desired state below
        
        // n1 comes back and demotes himself 
        assertEquals(memento1b.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.STANDBY);
        assertEquals(memento1b.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento1b.getMasterNodeId(), n2.ownNodeId);
        assertEquals(memento1b.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time2);
        assertEquals(memento1b.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time1);
        
        // n2 now sees itself as master, with n1 in standby again
        ManagementPlaneSyncRecord memento2c = n2.manager.getManagementPlaneSyncState();
        log.info(n2+" HA now: "+memento2c);
        assertEquals(memento2c.getManagementNodes().get(n1.ownNodeId).getStatus(), ManagementNodeState.STANDBY);
        assertEquals(memento2c.getManagementNodes().get(n2.ownNodeId).getStatus(), ManagementNodeState.MASTER);
        assertEquals(memento2c.getMasterNodeId(), n2.ownNodeId);
        assertEquals(memento2c.getManagementNodes().get(n1.ownNodeId).getRemoteTimestamp(), time2);
        assertEquals(memento2c.getManagementNodes().get(n2.ownNodeId).getRemoteTimestamp(), time2);

    }
    
}
