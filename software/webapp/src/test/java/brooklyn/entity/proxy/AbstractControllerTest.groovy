package brooklyn.entity.proxy

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.driver.MockSshDriver
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxying.EntitySpecs
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntityImpl
import brooklyn.util.flags.SetFromFlag

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables

public class AbstractControllerTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractControllerTest)
    
    TestApplication app
    Cluster cluster
    AbstractController controller
    
    FixedListMachineProvisioningLocation loc
    List<Collection<String>> updates

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        List<SshMachineLocation> machines = []
        for (i in 1..10) {
            machines << new SshMachineLocation(address:Inet4Address.getByName("1.1.1.$i"))
        }
        loc = new FixedListMachineProvisioningLocation<SshMachineLocation>(machines:machines)
        updates = new CopyOnWriteArrayList();
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        cluster = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", {flags,parent -> new ClusteredEntity(flags, parent)}));
        
        final AtomicInteger invokeCountForStart = new AtomicInteger(0);
        controller = new AbstractControllerImpl(
                parent:app, 
                serverPool:cluster, 
                portNumberSensor:ClusteredEntity.HTTP_PORT,
                domain:"mydomain") {

            @Override
            public void connectSensors() {
                super.connectSensors();
                setAttribute(SERVICE_UP, true);
            }
            
            @Override
            protected void reconfigureService() {
                log.info "test controller reconfigure, addresses $serverPoolAddresses"
                if ((serverPoolAddresses && !updates) || (updates && serverPoolAddresses!=updates.last())) {
                    updates.add(serverPoolAddresses)
                }
            }

            @Override
            public Class getDriverInterface() {
                return MockSshDriver.class;
            }
            public void reload() {
                // no-op
            }
        }
        Entities.manage(controller);
        app.start([loc])
    }
    
    @Test
    public void testUpdateCalledWithAddressesOfNewChildren() {
        // First child
        cluster.resize(1)
        EntityLocal child = cluster.children.first()
        
        def u = new ArrayList(updates);
        assertEquals(u, [], "expected empty list but got $u")
        
        child.setAttribute(ClusteredEntity.HTTP_PORT, 1234)
        child.setAttribute(Startable.SERVICE_UP, true)
        assertEventuallyAddressesMatchCluster()

        // Second child
        cluster.resize(2)
        executeUntilSucceeds { cluster.children.size() == 2 }
        EntityLocal child2 = cluster.children.asList().get(1)
        
        child2.setAttribute(ClusteredEntity.HTTP_PORT, 1234)
        child2.setAttribute(Startable.SERVICE_UP, true)
        assertEventuallyAddressesMatchCluster()
        
        // And remove all children; expect all addresses to go away
        cluster.resize(0);
        assertEventuallyAddressesMatchCluster();
    }

    @Test(groups = "Integration", invocationCount=10)
    public void testUpdateCalledWithAddressesOfNewChildrenManyTimes() {
        testUpdateCalledWithAddressesOfNewChildren();
    }
    
    @Test
    public void testUpdateCalledWithAddressesRemovedForStoppedChildren() {
        // Get some children, so we can remove one...
        cluster.resize(2)
        cluster.children.each {
            it.setAttribute(ClusteredEntity.HTTP_PORT, 1234)
            it.setAttribute(Startable.SERVICE_UP, true)
        }
        assertEventuallyAddressesMatchCluster()

        // Now remove one child
        cluster.resize(1)
        assertEquals(cluster.children.size(), 1)
        assertEventuallyAddressesMatchCluster()
    }

    @Test
    public void testUpdateCalledWithAddressesRemovedForServiceDownChildrenThatHaveClearedHostnamePort() {
        // Get some children, so we can remove one...
        cluster.resize(2)
        cluster.children.each {
            it.setAttribute(ClusteredEntity.HTTP_PORT, 1234)
            it.setAttribute(Startable.SERVICE_UP, true)
        }
        assertEventuallyAddressesMatchCluster()

        // Now unset host/port, and remove children
        // Note the unsetting of hostname is done in SoftwareProcessImpl.stop(), so this is realistic
        for (EntityLocal child : cluster.getChildren()) {
            child.setAttribute(ClusteredEntity.HTTP_PORT, null)
            child.setAttribute(ClusteredEntity.HOSTNAME, null)
            child.setAttribute(Startable.SERVICE_UP, false)
        }
        assertEventuallyAddressesMatch(ImmutableList.<Entity>of());
    }

    private void assertEventuallyAddressesMatchCluster() {
        assertEventuallyAddressesMatch(cluster.children);
    }

    private void assertEventuallyAddressesMatch(final Collection<Entity> expectedMembers) {
        executeUntilSucceeds(timeout:5000) {
            List<Collection<String>> u = new ArrayList(updates);
            Collection<String> last = Iterables.getLast(u, null);
            Collection<String> expectedAddresses = locationsToAddresses(1234, expectedMembers);
            log.debug "test ${u.size()} updates, expecting $expectedAddresses; actual $last"
            assertTrue(u.size() > 0);
            assertTrue(u.last() == expectedAddresses, "actual="+last+" expected="+expectedAddresses);
        }
    }

    private Collection<String> locationsToAddresses(int port, Entity... entities) {
        return locationsToAddresses(port, entities as List)
    }
        
    private Collection<String> locationsToAddresses(int port, Collection<Entity> entities) {
        Set<String> result = [] as Set
        entities.each {
            result << it.firstLocation().address.hostName+":"+port
        }
        return result
    }
}

class ClusteredEntity extends TestEntityImpl {
    public ClusteredEntity(Map flags=[:], Entity parent=null) { super(flags,parent) }
    public ClusteredEntity(Entity parent) { this([:],parent) }
    
    @SetFromFlag("hostname")
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    
    @SetFromFlag("port")
    public static final AttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT;
    
    MachineProvisioningLocation provisioner
    
    public void start(Collection<? extends Location> locs) {
        provisioner = locs.first()
        MachineLocation machine = provisioner.obtain([:]);
        addLocations([machine]);
        setAttribute(HOSTNAME, machine.address.hostName);
    }
    public void stop() {
        provisioner?.release(firstLocation())
    }
}
