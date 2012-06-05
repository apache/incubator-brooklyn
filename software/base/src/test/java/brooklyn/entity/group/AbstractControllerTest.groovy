package brooklyn.entity.group

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup
import brooklyn.entity.driver.MockSshBasedSoftwareSetup
import brooklyn.entity.trait.Startable
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestEntity

class AbstractControllerTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractControllerTest)
    
    AbstractApplication app
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
        
        app = new AbstractApplication() {}
        cluster = new DynamicCluster(owner:app, initialSize:0, factory:{flags,parent -> new ClusteredEntity(flags, parent)})
        
        final AtomicInteger invokeCountForStart = new AtomicInteger(0);
        controller = new AbstractController(
                owner:app, 
                cluster:cluster, 
                portNumberSensor:ClusteredEntity.MY_PORT,
                domain:"mydomain") {

            @Override
            protected void reconfigureService() {
                log.info "test controller reconfigure, addresses $addresses"
                if ((addresses && !updates) || (updates && addresses!=updates.last())) updates.add(addresses)
            }
            public SshBasedAppSetup newDriver(SshMachineLocation machine) {
                return new MockSshBasedSoftwareSetup(this, machine);
            }
        }
        app.getManagementContext().manage(app)
        app.start([loc])
    }
    
    @Test
    public void testUpdateCalledWithAddressesOfNewChildren() {
        // First child
        cluster.resize(1)
        EntityLocal child = cluster.ownedChildren.first()
        
        def u = new ArrayList(updates);
        assertEquals(u, [], "expected empty list but got $u")
        
        child.setAttribute(ClusteredEntity.MY_PORT, 1234)
        child.setAttribute(Startable.SERVICE_UP, true)
        assertEventuallyAddressesMatchCluster()

        // Second child
        cluster.resize(2)
        executeUntilSucceeds { cluster.ownedChildren.size() == 2 }
        EntityLocal child2 = cluster.ownedChildren.asList().get(1)
        
        child2.setAttribute(ClusteredEntity.MY_PORT, 1234)
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
            it.setAttribute(ClusteredEntity.MY_PORT, 1234)
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
            assertTrue(u.last() == locationsToAddresses(1234, cluster.ownedChildren), "actual="+u.last());
        }
    }
    
    private Collection<String> locationsToAddresses(int port, Entity... entities) {
        return locationsToAddresses(port, entities as List)
    }
        
    private Collection<String> locationsToAddresses(int port, Collection<Entity> entities) {
        Set<String> result = [] as Set
        entities.each {
            result << it.firstLocation().address.hostAddress+":"+port
        }
        return result
    }
}

class ClusteredEntity extends TestEntity {
    public ClusteredEntity(Map flags=[:], Entity owner=null) { super(flags,owner) }
    public ClusteredEntity(Entity owner) { this([:],owner) }
    
    public static final Sensor<Integer> MY_PORT = new BasicAttributeSensor<Integer>(Integer.class, "port", "My port");
    
    MachineProvisioningLocation provisioner
    
    public void start(Collection<? extends Location> locs) {
        provisioner = locs.first()
        locations << provisioner.obtain([:])
    }
    public void stop() {
        provisioner?.release(firstLocation())
    }
}
