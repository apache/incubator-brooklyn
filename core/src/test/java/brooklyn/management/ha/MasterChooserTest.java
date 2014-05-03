package brooklyn.management.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.BrooklynVersion;
import brooklyn.entity.rebind.plane.dto.BasicManagerMemento;
import brooklyn.management.ha.BasicMasterChooser.AlphabeticMasterChooser;
import brooklyn.management.ha.ManagementPlaneMementoPersisterInMemory.MutableManagementPlaneMemento;
import brooklyn.management.ha.ManagerMemento.HealthStatus;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableSet;

public class MasterChooserTest {

    private MutableManagementPlaneMemento memento;
    private AlphabeticMasterChooser chooser;
    private long now;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        memento = new MutableManagementPlaneMemento();
        chooser = new AlphabeticMasterChooser();
        now = System.currentTimeMillis();
    }
    
    @Test
    public void testChoosesFirstAlphanumeric() throws Exception {
        memento.addNode(newManagerMemento("node1",  HealthStatus.STANDBY, now));
        memento.addNode(newManagerMemento("node2",  HealthStatus.STANDBY, now));
        memento.addNode(newManagerMemento("node3",  HealthStatus.STANDBY, now));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        String ownNodeId = "node2";
        assertEquals(chooser.choose(memento, heartbeatTimeout, ownNodeId, now).getNodeId(), "node1");
    }
    
    @Test
    public void testReturnsNullIfNoValid() throws Exception {
        memento.addNode(newManagerMemento("node1", HealthStatus.STANDBY, now - 31*1000));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        assertNull(chooser.choose(memento, heartbeatTimeout, "node2", now));
    }
    
    @Test
    public void testFiltersOutByHeartbeat() throws Exception {
        memento.addNode(newManagerMemento("node1", HealthStatus.STANDBY, now - 31*1000));
        memento.addNode(newManagerMemento("node2", HealthStatus.STANDBY, now - 20*1000));
        memento.addNode(newManagerMemento("node3", HealthStatus.STANDBY, now));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        assertEquals(chooser.filterHealthy(memento, heartbeatTimeout, now).keySet(), ImmutableSet.of("node2", "node3"));
    }
    
    @Test
    public void testFiltersOutByStatus() throws Exception {
        memento.addNode(newManagerMemento("node1", HealthStatus.FAILED, now));
        memento.addNode(newManagerMemento("node2", HealthStatus.TERMINATED, now));
        memento.addNode(newManagerMemento("node3", null, now));
        memento.addNode(newManagerMemento("node4",  HealthStatus.STANDBY, now));
        memento.addNode(newManagerMemento("node5", HealthStatus.MASTER, now));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        assertEquals(chooser.filterHealthy(memento, heartbeatTimeout, now).keySet(), ImmutableSet.of("node4", "node5"));
    }
    
    private ManagerMemento newManagerMemento(String nodeId, HealthStatus status, long timestamp) {
        return BasicManagerMemento.builder().brooklynVersion(BrooklynVersion.get()).nodeId(nodeId).status(status).timestampUtc(timestamp).build();
    }
}
