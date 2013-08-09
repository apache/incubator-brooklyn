package brooklyn.entity.proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.driver.MockSshDriver;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class AbstractControllerTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractControllerTest.class);
    
    TestApplication app;
    Cluster cluster;
    AbstractController controller;
    
    FixedListMachineProvisioningLocation loc;
    List<Collection<String>> updates;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        List<SshMachineLocation> machines = new ArrayList<SshMachineLocation>();
        for (int i=1; i<=10; i++) {
            try {
                machines.add(new SshMachineLocation(MutableMap.of("address", Inet4Address.getByName("1.1.1."+i))));
            } catch (UnknownHostException e) {
                throw Exceptions.propagate(e);
            }
        }
        loc = new FixedListMachineProvisioningLocation<SshMachineLocation>(MutableMap.of("machines", machines));
        updates = new CopyOnWriteArrayList<Collection<String>>();
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", new ClusteredEntity.Factory()));
        
        final AtomicInteger invokeCountForStart = new AtomicInteger(0);
        controller = new AbstractControllerImpl(MutableMap.builder()
                .put("parent", app) 
                .put("serverPool", cluster) 
                .put("portNumberSensor", ClusteredEntity.HTTP_PORT)
                .put("domain", "mydomain").build()) {

            @Override
            public void connectSensors() {
                super.connectSensors();
                setAttribute(SERVICE_UP, true);
            }
            
            @Override
            protected void reconfigureService() {
                log.info("test controller reconfigure, addresses "+serverPoolAddresses);
                if ((!serverPoolAddresses.isEmpty() && updates.isEmpty()) || (!updates.isEmpty() && serverPoolAddresses!=updates.get(updates.size()-1))) {
                    updates.add(serverPoolAddresses);
                }
            }

            @Override
            public Class getDriverInterface() {
                return MockSshDriver.class;
            }
            public void reload() {
                // no-op
            }
        };
        Entities.manage(controller);
        app.start(Arrays.asList(loc));
    }
    
    @Test
    public void testUpdateCalledWithAddressesOfNewChildren() {
        // First child
        cluster.resize(1);
        EntityLocal child = (EntityLocal) cluster.getChildren().iterator().next();
        
        List<Collection<String>> u = Lists.newArrayList(updates);
        assertTrue(u.isEmpty(), "expected empty list but got "+u);
        
        child.setAttribute(ClusteredEntity.HTTP_PORT, 1234);
        child.setAttribute(Startable.SERVICE_UP, true);
        assertEventuallyAddressesMatchCluster();

        // Second child
        cluster.resize(2);
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(cluster.getChildren().size(), 2);
            }});
        EntityLocal child2 = (EntityLocal) Iterables.getOnlyElement(MutableSet.builder().addAll(cluster.getChildren()).remove(child).build());
        
        child2.setAttribute(ClusteredEntity.HTTP_PORT, 1234);
        child2.setAttribute(Startable.SERVICE_UP, true);
        assertEventuallyAddressesMatchCluster();
        
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
        cluster.resize(2);
        for (Entity it: cluster.getChildren()) { 
            ((EntityLocal)it).setAttribute(ClusteredEntity.HTTP_PORT, 1234);
            ((EntityLocal)it).setAttribute(Startable.SERVICE_UP, true);
        }
        assertEventuallyAddressesMatchCluster();

        // Now remove one child
        cluster.resize(1);
        assertEquals(cluster.getChildren().size(), 1);
        assertEventuallyAddressesMatchCluster();
    }

    @Test
    public void testUpdateCalledWithAddressesRemovedForServiceDownChildrenThatHaveClearedHostnamePort() {
        // Get some children, so we can remove one...
        cluster.resize(2);
        for (Entity it: cluster.getChildren()) { 
            ((EntityLocal)it).setAttribute(ClusteredEntity.HTTP_PORT, 1234);
            ((EntityLocal)it).setAttribute(Startable.SERVICE_UP, true);
        }
        assertEventuallyAddressesMatchCluster();

        // Now unset host/port, and remove children
        // Note the unsetting of hostname is done in SoftwareProcessImpl.stop(), so this is realistic
        for (Entity it : cluster.getChildren()) {
            ((EntityLocal)it).setAttribute(ClusteredEntity.HTTP_PORT, null);
            ((EntityLocal)it).setAttribute(ClusteredEntity.HOSTNAME, null);
            ((EntityLocal)it).setAttribute(Startable.SERVICE_UP, false);
        }
        assertEventuallyAddressesMatch(ImmutableList.<Entity>of());
    }

    private void assertEventuallyAddressesMatchCluster() {
        assertEventuallyAddressesMatch(cluster.getChildren());
    }

    private void assertEventuallyAddressesMatch(final Collection<Entity> expectedMembers) {
        Asserts.succeedsEventually(MutableMap.of("timeout", 15000), new Runnable() {
                @Override public void run() {
                    List<Collection<String>> u = Lists.newArrayList(updates);
                    Collection<String> last = Iterables.getLast(u, null);
                    Collection<String> expectedAddresses = locationsToAddresses(1234, expectedMembers);
                    log.debug("test "+u.size()+" updates, expecting "+expectedAddresses+"; actual "+last);
                    assertTrue(u.size() > 0);
                    assertEquals(ImmutableSet.copyOf(Iterables.getLast(u)), ImmutableSet.copyOf(expectedAddresses), "actual="+last+" expected="+expectedAddresses);
                }} );
    }

    private Collection<String> locationsToAddresses(int port, Collection<Entity> entities) {
        Set<String> result = MutableSet.of();
        for (Entity e: entities) {
            result.add( ((SshMachineLocation) e.getLocations().iterator().next()) .getAddress().getHostName()+":"+port);
        }
        return result;
    }

    public static class ClusteredEntity extends TestEntityImpl {
        public static class Factory implements EntityFactory<ClusteredEntity> {
            @Override
            public ClusteredEntity newEntity(Map flags, Entity parent) {
                return new ClusteredEntity(flags, parent);
            }
        }
        public ClusteredEntity(Map flags, Entity parent) { super(flags,parent); }
        public ClusteredEntity(Entity parent) { super(MutableMap.of(),parent); }
        public ClusteredEntity(Map flags) { super(flags,null); }
        public ClusteredEntity() { super(MutableMap.of(),null); }
        
        @SetFromFlag("hostname")
        public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
        
        @SetFromFlag("port")
        public static final AttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT;
        
        MachineProvisioningLocation provisioner;
        
        public void start(Collection<? extends Location> locs) {
            provisioner = (MachineProvisioningLocation) locs.iterator().next();
            MachineLocation machine;
            try {
                machine = provisioner.obtain(MutableMap.of());
            } catch (NoMachinesAvailableException e) {
                throw Exceptions.propagate(e);
            }
            addLocations(Arrays.asList(machine));
            setAttribute(HOSTNAME, machine.getAddress().getHostName());
        }
        public void stop() {
            if (provisioner!=null) provisioner.release((MachineLocation) firstLocation());
        }
    }
}
