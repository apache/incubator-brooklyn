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
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.driver.MockSshDriver
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.flags.SetFromFlag

class AbstractControllerTest {

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
        
        app = new TestApplication()
        cluster = new DynamicCluster(owner:app, initialSize:0, factory:{flags,parent -> new ClusteredEntity(flags, parent)})
        
        final AtomicInteger invokeCountForStart = new AtomicInteger(0);
        controller = new AbstractController(
                owner:app, 
                serverPool:cluster, 
                portNumberSensor:ClusteredEntity.HTTP_PORT,
                domain:"mydomain") {

            @Override
            public void connectSensors() {
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
        app.startManagement();
        app.start([loc])
    }
    
    @Test
    public void testUpdateCalledWithAddressesOfNewChildren() {
        // First child
        cluster.resize(1)
        EntityLocal child = cluster.ownedChildren.first()
        
        def u = new ArrayList(updates);
        assertEquals(u, [], "expected empty list but got $u")
        
        child.setAttribute(ClusteredEntity.HTTP_PORT, 1234)
        child.setAttribute(Startable.SERVICE_UP, true)
        assertEventuallyAddressesMatchCluster()

        // Second child
        cluster.resize(2)
        executeUntilSucceeds { cluster.ownedChildren.size() == 2 }
        EntityLocal child2 = cluster.ownedChildren.asList().get(1)
        
        child2.setAttribute(ClusteredEntity.HTTP_PORT, 1234)
        child2.setAttribute(Startable.SERVICE_UP, true)
        assertEventuallyAddressesMatchCluster()
    }

    @Test(groups = "Integration")
    public void testUpdateCalledWithAddressesOfNewChildrenManyTimes() {
        for (int i=0; i<10; i++) {
            try {
                log.info("testUpdateCalledWithAddressesOfNewChildren #"+i);
                testUpdateCalledWithAddressesOfNewChildren();
                cluster.resize(0);
                assertEventuallyAddressesMatchCluster();
                updates.clear();
            } catch (Throwable e) {
                log.warn "testUpdateCalledWithAddressesOfNewChildren, #"+i+" failed: "+e
                throw e;
            }
        }
    }
    
    @Test
    public void testUpdateCalledWithAddressesRemovedForStoppedChildren() {
        // Get some children, so we can remove one...
        cluster.resize(2)
        cluster.ownedChildren.each {
            it.setAttribute(ClusteredEntity.HTTP_PORT, 1234)
            it.setAttribute(Startable.SERVICE_UP, true)
        }
        assertEventuallyAddressesMatchCluster()

        // Now remove one child
        cluster.resize(1)
        assertEquals(cluster.ownedChildren.size(), 1)
        assertEventuallyAddressesMatchCluster()
    }

    private void assertEventuallyAddressesMatchCluster() {
        executeUntilSucceeds(timeout:5000) {
            def u = new ArrayList(updates);
            log.debug "test ${u.size()} updates, expecting ${locationsToAddresses(1234, cluster.ownedChildren)} = ${u ? u.last() : 'empty'}"
            assertTrue(u.size() > 0);
            assertTrue(u.last() == locationsToAddresses(1234, cluster.ownedChildren), "actual="+u.last()+" expected="+locationsToAddresses(1234, cluster.ownedChildren));
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

class ClusteredEntity extends TestEntity {
    public ClusteredEntity(Map flags=[:], Entity owner=null) { super(flags,owner) }
    public ClusteredEntity(Entity owner) { this([:],owner) }
    
    @SetFromFlag("hostname")
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    
    @SetFromFlag("port")
    public static final AttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT;
    
    MachineProvisioningLocation provisioner
    
    public void start(Collection<? extends Location> locs) {
        provisioner = locs.first()
        MachineLocation machine = provisioner.obtain([:]);
        locations << machine
        setAttribute(HOSTNAME, machine.address.hostName);
    }
    public void stop() {
        provisioner?.release(firstLocation())
    }
}
