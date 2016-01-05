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
package org.apache.brooklyn.entity.group;

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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.EntityFactory;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.BlockingEntity;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.repeat.Repeater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DynamicFabricTest extends BrooklynAppUnitTestSupport {
    private static final Logger log = LoggerFactory.getLogger(DynamicFabricTest.class);

    private static final int TIMEOUT_MS = 5*1000;

    private Location loc1;
    private Location loc2;
    private Location loc3;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc1 = new SimulatedLocation();
        loc2 = new SimulatedLocation();
        loc3 = new SimulatedLocation();
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
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("memberSpec", EntitySpec.create(TestEntity.class)));
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
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
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
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) {
                    TestEntity result = app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
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
        final DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map fabricProperties, Entity parent) {
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(DynamicCluster.class)
                            .parent(parent) 
                            .configure("initialSize", 0)
                            .configure("factory", new EntityFactory<Entity>() {
                                @Override public Entity newEntity(Map clusterProperties, Entity parent) { 
                                    return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
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
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) {
                    CountDownLatch latch = new CountDownLatch(1);
                    latches.add(latch);
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(BlockingEntity.class)
                            .parent(parent)
                            .configure(flags)
                            .configure(BlockingEntity.STARTUP_LATCH, latch));
                }}));
        final Collection<Location> locs = ImmutableList.of(loc1, loc2);

        final Task<?> task = fabric.invoke(Startable.START, ImmutableMap.of("locations", locs));

        new Repeater("Wait until each task is executing")
                .every(100, TimeUnit.MILLISECONDS)
                .limitTimeTo(30, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                        @Override public Boolean call() {
                            return latches.size() == locs.size();
                        }})
                .runRequiringTrue();

        assertFalse(task.isDone());

        for (CountDownLatch latch : latches) {
            latch.countDown();
        }

        new Repeater("Wait until complete")
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
        final DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) {
                    CountDownLatch shutdownLatch = new CountDownLatch(1);
                    CountDownLatch executingShutdownNotificationLatch = new CountDownLatch(1);
                    shutdownLatches.add(shutdownLatch);
                    executingShutdownNotificationLatches.add(executingShutdownNotificationLatch);
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(BlockingEntity.class)
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
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) { 
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(BasicEntity.class)
                            .parent(parent)
                            .configure(flags));
                }}));

        try {
            fabric.start(ImmutableList.of(loc1));
            assertEquals(fabric.getChildren().size(), 1);
        } catch (Exception e) {
            Throwable unwrapped = Exceptions.getFirstInteresting(e);
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
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", new EntityFactory<Entity>() {
                @Override public Entity newEntity(Map flags, Entity parent) { 
                    return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                            .parent(parent)
                            .configure(flags));
                }}));

        fabric.start(ImmutableList.of(loc1));
        
        BasicEntity extraChild = fabric.addChild(EntitySpec.create(BasicEntity.class));

        fabric.stop();
    }

    @Test
    public void testDynamicFabricPropagatesProperties() throws Exception {
        final EntityFactory<Entity> entityFactory = new EntityFactory<Entity>() {
            @Override public Entity newEntity(Map flags, Entity parent) {
                return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                        .parent(parent)
                        .configure(flags)
                        .configure("b", "avail"));
            }};

            final EntityFactory<Entity> clusterFactory = new EntityFactory<Entity>() {
            @Override public Entity newEntity(Map flags, Entity parent) {
                return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(DynamicCluster.class)
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
            
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure("factory", clusterFactory)
            .configure("customChildFlags", ImmutableMap.of("fromFabric", "passed to cluster but not base entity"))
            .configure(Attributes.HTTP_PORT, PortRanges.fromInteger(1234))); // for inheritance by children (as a port range)

        app.start(ImmutableList.of(loc1));

        assertEquals(fabric.getChildren().size(), 1);
        DynamicCluster child = (DynamicCluster) getChild(fabric, 0);
        assertEquals(child.getMembers().size(), 1);
        assertEquals(getMember(child, 0).getConfig(Attributes.HTTP_PORT.getConfigKey()), PortRanges.fromInteger(1234));
        assertEquals(((TestEntity)getMember(child, 0)).getConfigureProperties().get("a"), null);
        assertEquals(((TestEntity)getMember(child, 0)).getConfigureProperties().get("b"), "avail");
        assertEquals(((TestEntity)getMember(child, 0)).getConfigureProperties().get("fromCluster"), "passed to base entity");
        assertEquals(((TestEntity)getMember(child, 0)).getConfigureProperties().get("fromFabric"), null);

        child.resize(2);
        assertEquals(child.getMembers().size(), 2);
        assertEquals(getGrandchild(fabric, 0, 1).getConfig(Attributes.HTTP_PORT.getConfigKey()), PortRanges.fromInteger(1234));
        assertEquals(((TestEntity)getMember(child, 1)).getConfigureProperties().get("a"), null);
        assertEquals(((TestEntity)getMember(child, 1)).getConfigureProperties().get("b"), "avail");
        assertEquals(((TestEntity)getMember(child, 1)).getConfigureProperties().get("fromCluster"), "passed to base entity");
        assertEquals(((TestEntity)getMember(child, 1)).getConfigureProperties().get("fromFabric"), null);
    }

    @Test
    public void testExistingChildrenStarted() throws Exception {
        List<Location> locs = ImmutableList.of(loc1, loc2, loc3);
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure(DynamicFabric.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));
        
        List<TestEntity> existingChildren = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            existingChildren.add(fabric.addChild(EntitySpec.create(TestEntity.class)));
        }
        app.start(locs);

        // Expect only these existing children
        Asserts.assertEqualsIgnoringOrder(fabric.getChildren(), existingChildren);
        Asserts.assertEqualsIgnoringOrder(fabric.getMembers(), existingChildren);

        // Expect one location per existing child
        List<Location> remainingLocs = MutableList.copyOf(locs);
        for (Entity existingChild : existingChildren) {
            Collection<Location> childLocs = existingChild.getLocations();
            assertEquals(childLocs.size(), 1, "childLocs="+childLocs);
            assertTrue(remainingLocs.removeAll(childLocs));
        }
    }

    @Test
    public void testExistingChildrenStartedRoundRobiningAcrossLocations() throws Exception {
        List<Location> locs = ImmutableList.of(loc1, loc2);
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure(DynamicFabric.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));
        
        List<TestEntity> existingChildren = Lists.newArrayList();
        for (int i = 0; i < 4; i++) {
            existingChildren.add(fabric.addChild(EntitySpec.create(TestEntity.class)));
        }
        app.start(locs);

        // Expect only these existing children
        Asserts.assertEqualsIgnoringOrder(fabric.getChildren(), existingChildren);
        Asserts.assertEqualsIgnoringOrder(fabric.getMembers(), existingChildren);

        // Expect one location per existing child (round-robin)
        // Expect one location per existing child
        List<Location> remainingLocs = MutableList.<Location>builder().addAll(locs).addAll(locs).build();
        for (Entity existingChild : existingChildren) {
            Collection<Location> childLocs = existingChild.getLocations();
            assertEquals(childLocs.size(), 1, "childLocs="+childLocs);
            assertTrue(remainingLocs.remove(Iterables.get(childLocs, 0)), "childLocs="+childLocs+"; remainingLocs="+remainingLocs+"; allLocs="+locs);
        }
    }

    @Test
    public void testExistingChildrenToppedUpWhenNewMembersIfMoreLocations() throws Exception {
        List<Location> locs = ImmutableList.of(loc1, loc2, loc3);
        
        DynamicFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicFabric.class)
            .configure(DynamicFabric.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));
        
        TestEntity existingChild = fabric.addChild(EntitySpec.create(TestEntity.class));
        
        app.start(locs);

        // Expect three children: the existing one, and one per other location
        assertEquals(fabric.getChildren().size(), 3, "children="+fabric.getChildren());
        assertTrue(fabric.getChildren().contains(existingChild), "children="+fabric.getChildren()+"; existingChild="+existingChild);
        Asserts.assertEqualsIgnoringOrder(fabric.getMembers(), fabric.getChildren());

        List<Location> remainingLocs = MutableList.<Location>builder().addAll(locs).build();
        for (Entity child : fabric.getChildren()) {
            Collection<Location> childLocs = child.getLocations();
            assertEquals(childLocs.size(), 1, "childLocs="+childLocs);
            assertTrue(remainingLocs.remove(Iterables.get(childLocs, 0)), "childLocs="+childLocs+"; remainingLocs="+remainingLocs+"; allLocs="+locs);
        }
    }

    private Entity getGrandchild(Entity entity, int childIndex, int grandchildIndex) {
        Entity child = getChild(entity, childIndex);
        return Iterables.get(child.getChildren(), grandchildIndex);
    }

    private Entity getChild(Entity entity, int childIndex) {
        return Iterables.get(entity.getChildren(), childIndex);
    }
    
    private Entity getMember(Group entity, int memberIndex) {
        return Iterables.get(entity.getMembers(), memberIndex);
    }
}
