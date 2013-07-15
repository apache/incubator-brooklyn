package brooklyn.entity.group;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.BlockingEntity;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.Repeater;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DynamicFabricTest {
    private static final Logger log = LoggerFactory.getLogger(DynamicFabricTest.class);

    private static final int TIMEOUT_MS = 5*1000;
    
    private TestApplication app;
    private Location loc1;
    private Location loc2;
    private Location loc3;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc1 = new SimulatedLocation();
        loc2 = new SimulatedLocation();
        loc3 = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testDynamicFabricUsesMemberSpecToCreateAndStartEntityWhenGivenSingleLocation() throws Exception {
        runWithEntitySpecWithLocations(ImmutableList.of(loc1));
    }

    @Test
    public void testDynamicFabricUsesMemberSpecToCreateAndStartsEntityWhenGivenManyLocations() throws Exception {
        runWithEntitySpecWithLocations(ImmutableList.of(loc1,loc2,loc3));
    }
    
    private void runWithEntitySpecWithLocations(Collection<Location> locs) {
        Collection<Location> unclaimedLocs = Lists.newArrayList(locs);
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("memberSpec", EntitySpecs.spec(TestEntity.class)));
        app.start(locs);
        
        assertEquals(fabric.getChildren().size(), locs.size(), Joiner.on(",").join(fabric.getChildren()));
        assertEquals(fabric.getMembers().size(), locs.size(), "members="+fabric.getMembers());
        assertEquals(ImmutableSet.copyOf(fabric.getMembers()), ImmutableSet.copyOf(fabric.getChildren()), "members="+fabric.getMembers()+"; children="+fabric.getChildren());
        
        for (Entity it : fabric.getChildren()) {
            TestEntity child = (TestEntity) it;
            assertEquals(child.getCounter().get(), 1);
            assertEquals(child.getLocations().size(), 1, Joiner.on(",").join(child.getLocations()));
            assertTrue(unclaimedLocs.removeAll(child.getLocations()));
        }
        assertTrue(unclaimedLocs.isEmpty(), Joiner.on(",").join(unclaimedLocs));
    }
    
    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenSingleLocation() throws Exception {
        runWithFactoryWithLocations(ImmutableList.of(loc1));
    }

    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenManyLocations() throws Exception {
        runWithFactoryWithLocations(ImmutableList.of(loc1, loc2, loc3));
    }
    
    private void runWithFactoryWithLocations(Collection<Location> locs) {
        Collection<Location> unclaimedLocs = Lists.newArrayList(locs);
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(TestEntity.class)
                                .parent(parent)
                                .configure(flags));
                    }}));
        app.start(locs);
        
        assertEquals(fabric.getChildren().size(), locs.size(), Joiner.on(",").join(fabric.getChildren()));
        assertEquals(fabric.getMembers().size(), locs.size(), "members="+fabric.getMembers());
        assertEquals(ImmutableSet.copyOf(fabric.getMembers()), ImmutableSet.copyOf(fabric.getChildren()), "members="+fabric.getMembers()+"; children="+fabric.getChildren());
        
        for (Entity it : fabric.getChildren()) {
            TestEntity child = (TestEntity) it;
            assertEquals(child.getCounter().get(), 1);
            assertEquals(child.getLocations().size(), 1, Joiner.on(",").join(child.getLocations()));
            assertTrue(unclaimedLocs.removeAll(child.getLocations()));
        }
        assertTrue(unclaimedLocs.isEmpty(), Joiner.on(",").join(unclaimedLocs));
    }
    
    @Test
    public void testNotifiesPostStartListener() throws Exception {
        final List<Entity> entitiesAdded = new CopyOnWriteArrayList<Entity>();
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) {
                    TestEntity result = app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(TestEntity.class)
                            .parent(parent)
                            .configure(flags));
                    entitiesAdded.add(result);
                    return result;
                }}));
        
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEquals(entitiesAdded.size(), 2);
        assertEquals(ImmutableSet.copyOf(entitiesAdded), ImmutableSet.copyOf(fabric.getChildren()));
    }
    
    @Test
    public void testSizeEnricher() throws Exception {
        Collection<Location> locs = ImmutableList.of(loc1, loc2, loc3);
        final DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map fabricProperties, Entity parent) {
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(DynamicCluster.class)
                            .parent(parent) 
                            .configure("initialSize", 0)
                            .configure("factory", new EntityFactory<Entity>() {
                                @Override public Entity newEntity(Map clusterProperties, Entity parent) { 
                                    return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(TestEntity.class)
                                            .parent(parent)
                                            .configure(clusterProperties));
                                }}));
                }}));
        app.start(locs);
        
        final AtomicInteger i = new AtomicInteger();
        final AtomicInteger total = new AtomicInteger();
        
        assertEquals(fabric.getChildren().size(), locs.size(), Joiner.on(",").join(fabric.getChildren()));
        for (Entity it : fabric.getChildren()) {
            Cluster child = (Cluster) it;
            total.addAndGet(i.incrementAndGet());
            child.resize(i.get());
        }
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(fabric.getAttribute(DynamicFabric.FABRIC_SIZE), (Integer) total.get());
                assertEquals(fabric.getFabricSize(), (Integer) total.get());
            }});
    }
    
    @Test
    public void testDynamicFabricStartsEntitiesInParallel() throws Exception {
        final List<CountDownLatch> latches = Lists.newCopyOnWriteArrayList();
        DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) {
                    CountDownLatch latch = new CountDownLatch(1); 
                    latches.add(latch);
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(BlockingEntity.class)
                            .parent(parent)
                            .configure(flags)
                            .configure(BlockingEntity.STARTUP_LATCH, latch));
                }}));
        final Collection<Location> locs = ImmutableList.of(loc1, loc2);
        
        final Task<?> task = fabric.invoke(Startable.START, ImmutableMap.of("locations", locs));

        new Repeater("Wait until each task is executing")
                .repeat()
                .every(100, TimeUnit.MILLISECONDS)
                .limitTimeTo(30, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                        @Override public Boolean call() {
                            return latches.size() == locs.size();
                        }})
                .run();

        assertFalse(task.isDone());
        
        for (CountDownLatch latch : latches) {
            latch.countDown();
        }
               
        new Repeater("Wait until complete")
                .repeat()
                .every(100, TimeUnit.MILLISECONDS)
                .limitTimeTo(30, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                        @Override public Boolean call() {
                            return task.isDone();
                        }})
                .run();

        assertEquals(fabric.getChildren().size(), locs.size(), Joiner.on(",").join(fabric.getChildren()));
                
        for (Entity it : fabric.getChildren()) {
            assertEquals(((TestEntity)it).getCounter().get(), 1);
        }
    }

    @Test(groups="Integration")
    public void testDynamicFabricStopsEntitiesInParallelManyTimes() throws Exception {
        for (int i=0; i<100; i++) {
            log.info("running testDynamicFabricStopsEntitiesInParallel iteration $i");
            testDynamicFabricStopsEntitiesInParallel();
        }
    }
    
    @Test
    public void testDynamicFabricStopsEntitiesInParallel() throws Exception {
        final List<CountDownLatch> shutdownLatches = Lists.newCopyOnWriteArrayList();
        final List<CountDownLatch> executingShutdownNotificationLatches = Lists.newCopyOnWriteArrayList();
        final DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) {
                    CountDownLatch shutdownLatch = new CountDownLatch(1); 
                    CountDownLatch executingShutdownNotificationLatch = new CountDownLatch(1); 
                    shutdownLatches.add(shutdownLatch);
                    executingShutdownNotificationLatches.add(executingShutdownNotificationLatch);
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(BlockingEntity.class)
                            .parent(parent)
                            .configure(flags)
                            .configure(BlockingEntity.SHUTDOWN_LATCH, shutdownLatch)
                            .configure(BlockingEntity.EXECUTING_SHUTDOWN_NOTIFICATION_LATCH, executingShutdownNotificationLatch));
                }}));
        Collection<Location> locs = ImmutableList.of(loc1, loc2);
        
        // Start the fabric (and check we have the required num things to concurrently stop)
        fabric.start(locs);
        
        assertEquals(shutdownLatches.size(), locs.size());
        assertEquals(executingShutdownNotificationLatches.size(), locs.size());
        assertEquals(fabric.getChildren().size(), locs.size());
        Collection<Entity> children = fabric.getChildren();
        
        // On stop, expect each child to get as far as blocking on its latch
        final Task<?> task = fabric.invoke(Startable.STOP, ImmutableMap.<String,Object>of());

        for (CountDownLatch it : executingShutdownNotificationLatches) {
            assertTrue(it.await(10*1000, TimeUnit.MILLISECONDS));
        }
        assertFalse(task.isDone());
        
        // When we release the latches, expect shutdown to complete
        for (CountDownLatch latch : shutdownLatches) {
            latch.countDown();
        }
        
        Asserts.succeedsEventually(MutableMap.of("timeout", 10*1000), new Runnable() {
            public void run() {
                assertTrue(task.isDone());
            }});

        Asserts.succeedsEventually(MutableMap.of("timeout", 10*1000), new Runnable() {
            public void run() {
                for (Entity it : fabric.getChildren()) {
                    int count = ((TestEntity)it).getCounter().get();
                    assertEquals(count, 0, it+" counter reports "+count);
                }
            }});
    }
    
    @Test
    public void testDynamicFabricDoesNotAcceptUnstartableChildren() throws Exception {
        DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) { 
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(BasicEntity.class)
                            .parent(parent)
                            .configure(flags));
                }}));
        
        try {
            fabric.start(ImmutableList.of(loc1));
            assertEquals(fabric.getChildren().size(), 1);
        } catch (Exception e) {
            Throwable unwrapped = TestUtils.unwrapThrowable(e);
            if (unwrapped instanceof IllegalStateException && unwrapped.getMessage() != null && (unwrapped.getMessage().contains("is not Startable"))) {
                // success
            } else {
                throw e;
            }
        }
    }
    
    // For follow-the-sun, a valid pattern is to associate the FollowTheSunModel as a child of the dynamic-fabric.
    // Thus we have "unstoppable" entities. Let's be relaxed about it, rather than blowing up.
    @Test
    public void testDynamicFabricIgnoresExtraUnstoppableChildrenOnStop() throws Exception {
        DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) { 
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(TestEntity.class)
                            .parent(parent)
                            .configure(flags));
                }}));
        
        fabric.start(ImmutableList.of(loc1));
        
        BasicEntity extraChild = app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(BasicEntity.class)
                .parent(fabric));
        Entities.manage(extraChild);
        
        fabric.stop();
    }
    
	@Test
    public void testDynamicFabricPropagatesProperties() throws Exception {
		final EntityFactory<Entity> entityFactory = new EntityFactory<Entity>() {
            @Override public Entity newEntity(Map flags, Entity parent) {
                return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(TestEntity.class)
                        .parent(parent)
                        .configure(flags)
                        .configure("b", "avail"));
            }};
	
            final EntityFactory<Entity> clusterFactory = new EntityFactory<Entity>() {
            @Override public Entity newEntity(Map flags, Entity parent) {
                return app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(DynamicCluster.class)
                        .parent(parent)
                        .configure(flags)
                        .configure("initialSize", 1)
                        .configure("factory", entityFactory)
                        .configure("customChildFlags", ImmutableMap.of("fromCluster", "passed to base entity"))
                        .configure("a", "ignored"));
                    // FIXME What to do about overriding DynamicCluster to do customChildFlags?
    //            new DynamicClusterImpl(clusterProperties) {
    //                protected Map getCustomChildFlags() { [fromCluster: "passed to base entity"] }
            }};
            
        DynamicFabric fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure("factory", clusterFactory)
            .configure("customChildFlags", ImmutableMap.of("fromFabric", "passed to cluster but not base entity"))
            .configure(Attributes.HTTP_PORT, PortRanges.fromInteger(1234))); // for inheritance by children (as a port range)
        
		app.start(ImmutableList.of(loc1));
        
		assertEquals(fabric.getChildren().size(), 1);
		assertEquals(getChild(fabric, 0).getChildren().size(), 1);
		assertEquals(getGrandchild(fabric, 0, 0).getConfig(Attributes.HTTP_PORT.getConfigKey()), PortRanges.fromInteger(1234));
		assertEquals(((TestEntity)getGrandchild(fabric, 0, 0)).getConfigureProperties().get("a"), null);
		assertEquals(((TestEntity)getGrandchild(fabric, 0, 0)).getConfigureProperties().get("b"), "avail");
		assertEquals(((TestEntity)getGrandchild(fabric, 0, 0)).getConfigureProperties().get("fromCluster"), "passed to base entity");
		assertEquals(((TestEntity)getGrandchild(fabric, 0, 0)).getConfigureProperties().get("fromFabric"), null);
        
        ((DynamicCluster)getChild(fabric, 0)).resize(2);
        assertEquals(getChild(fabric, 0).getChildren().size(), 2);
        assertEquals(getGrandchild(fabric, 0, 1).getConfig(Attributes.HTTP_PORT.getConfigKey()), PortRanges.fromInteger(1234));
        assertEquals(((TestEntity)getGrandchild(fabric, 0, 1)).getConfigureProperties().get("a"), null);
        assertEquals(((TestEntity)getGrandchild(fabric, 0, 1)).getConfigureProperties().get("b"), "avail");
        assertEquals(((TestEntity)getGrandchild(fabric, 0, 1)).getConfigureProperties().get("fromCluster"), "passed to base entity");
        assertEquals(((TestEntity)getGrandchild(fabric, 0, 1)).getConfigureProperties().get("fromFabric"), null);
	}
	
	private Entity getGrandchild(Entity entity, int childIndex, int grandchildIndex) {
        Entity child = getChild(entity, childIndex);
        return Iterables.get(child.getChildren(), grandchildIndex);
	}
	
    private Entity getChild(Entity entity, int childIndex) {
        return Iterables.get(entity.getChildren(), childIndex);
    }
}
