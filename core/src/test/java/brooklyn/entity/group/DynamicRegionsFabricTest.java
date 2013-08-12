package brooklyn.entity.group;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamicRegionsFabricTest {

    private TestApplication app;
    DynamicRegionsFabric fabric;
    private Location loc1;
    private Location loc2;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc1 = new SimulatedLocation();
        loc2 = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        
        fabric = app.createAndManageChild(EntitySpec.create(DynamicRegionsFabric.class)
                .configure("memberSpec", EntitySpec.create(TestEntity.class)));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testUsesInitialLocations() throws Exception {
        app.start(ImmutableList.of(loc1, loc2));

        assertEquals(fabric.getChildren().size(), 2, "children="+fabric.getChildren());
        assertEquals(fabric.getMembers().size(), 2, "members="+fabric.getMembers());
        assertEqualsIgnoringOrder(fabric.getChildren(), fabric.getMembers());
        assertEqualsIgnoringOrder(getLocationsOfChildren(fabric), ImmutableList.of(loc1, loc2));
    }
    
    @Test
    public void testDynamicallyAddLocations() throws Exception {
        app.start(ImmutableList.of(loc1));
        Set<Entity> initialChildren = ImmutableSet.copyOf(fabric.getChildren());
        Collection<Location> initialLocations = fabric.getLocations();
        assertEquals(initialChildren.size(), 1, "children="+initialChildren);
        assertEqualsIgnoringOrder(initialLocations, ImmutableSet.of(loc1));

        fabric.addRegion("localhost:(name=newloc1)");
        
        Set<Entity> newChildren = Sets.difference(ImmutableSet.copyOf(fabric.getChildren()), initialChildren);
        Collection<Location> newLocations = Iterables.getOnlyElement(newChildren).getLocations();
        assertEquals(Iterables.getOnlyElement(newLocations).getDisplayName(), "newloc1");
        
        Set<Location> newLocations2 = Sets.difference(ImmutableSet.copyOf(fabric.getLocations()), ImmutableSet.copyOf(initialLocations));
        assertEquals(newLocations2, ImmutableSet.copyOf(newLocations));
    }
    
    @Test
    public void testDynamicallyRemoveInitialLocations() throws Exception {
        app.start(ImmutableList.of(loc1, loc2));
        Set<Entity> initialChildren = ImmutableSet.copyOf(fabric.getChildren());

        Entity childToRemove1 = Iterables.get(initialChildren, 0);
        Entity childToRemove2 = Iterables.get(initialChildren, 1);
        
        // remove first child (leaving one)
        fabric.removeRegion(childToRemove1.getId());
        
        Set<Entity> removedChildren = Sets.difference(initialChildren, ImmutableSet.copyOf(fabric.getChildren()));
        Set<Entity> removedMembers = Sets.difference(initialChildren, ImmutableSet.copyOf(fabric.getMembers()));

        assertEqualsIgnoringOrder(removedChildren, ImmutableSet.of(childToRemove1));
        assertEqualsIgnoringOrder(removedMembers, ImmutableSet.of(childToRemove1));
        assertEqualsIgnoringOrder(fabric.getLocations(), childToRemove2.getLocations());
        
        // remove second child (leaving none)
        fabric.removeRegion(childToRemove2.getId());
        
        assertEqualsIgnoringOrder(fabric.getChildren(), ImmutableSet.of());
        assertEqualsIgnoringOrder(fabric.getMembers(), ImmutableSet.of());
        assertEqualsIgnoringOrder(fabric.getLocations(), ImmutableSet.of());
    }
    
    @Test
    public void testDynamicallyRemoveDynamicallyAddedLocation() throws Exception {
        app.start(ImmutableList.of(loc1));
        Set<Entity> initialChildren = ImmutableSet.copyOf(fabric.getChildren());
        Collection<Location> initialLocations = fabric.getLocations();
        assertEquals(initialChildren.size(), 1, "children="+initialChildren);
        assertEqualsIgnoringOrder(initialLocations, ImmutableSet.of(loc1));

        fabric.addRegion("localhost:(name=newloc1)");
        Entity childToRemove = Iterables.getOnlyElement(Sets.difference(ImmutableSet.copyOf(fabric.getChildren()), initialChildren));
        
        // remove dynamically added child
        fabric.removeRegion(childToRemove.getId());
        
        assertEqualsIgnoringOrder(fabric.getChildren(), initialChildren);
        assertEqualsIgnoringOrder(fabric.getLocations(), initialLocations);
    }
    
    @Test
    public void testRemoveRegionWithNonChild() throws Exception {
        app.start(ImmutableList.of(loc1));

        try {
            fabric.removeRegion(app.getId());
        } catch (Exception e) {
            IllegalArgumentException cause = Exceptions.getFirstThrowableOfType(e, IllegalArgumentException.class);
            if (cause == null && !e.toString().contains("Wrong parent")) throw e;
        }
    }
    
    @Test
    public void testRemoveRegionWithNonEntityId() throws Exception {
        app.start(ImmutableList.of(loc1));

        try {
            fabric.removeRegion("thisIsNotAnEntityId");
        } catch (Exception e) {
            IllegalArgumentException cause = Exceptions.getFirstThrowableOfType(e, IllegalArgumentException.class);
            if (cause == null && !e.toString().contains("No entity found")) throw e;
        }
    }
    
    private List<Location> getLocationsOfChildren(DynamicRegionsFabric fabric) {
        List<Location> result = Lists.newArrayList();
        for (Entity child : fabric.getChildren()) {
            result.addAll(child.getLocations());
        }
        return result;
    }

    private void assertEqualsIgnoringOrder(Iterable<? extends Object> col1, Iterable<? extends Object> col2) {
        assertEquals(Iterables.size(col1), Iterables.size(col2), "col2="+col1+"; col2="+col2);
        assertEquals(MutableSet.copyOf(col1), MutableSet.copyOf(col2), "col1="+col1+"; col2="+col2);
    }
}
