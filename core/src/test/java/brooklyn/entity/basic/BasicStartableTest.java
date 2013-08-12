package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartable.LocationsFilter;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class BasicStartableTest {

    private ManagementContext managementContext;
    private SimulatedLocation loc1;
    private SimulatedLocation loc2;
    private TestApplication app;
    private BasicStartable startable;
    private TestEntity entity;
    private TestEntity entity2;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        loc1 = managementContext.getLocationManager().createLocation(LocationSpec.spec(SimulatedLocation.class));
        loc2 = managementContext.getLocationManager().createLocation(LocationSpec.spec(SimulatedLocation.class));
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testDefaultIsAllLocations() throws Exception {
        startable = app.addChild(EntitySpec.create(BasicStartable.class));
        entity = startable.addChild(EntitySpec.create(TestEntity.class));
        entity2 = startable.addChild(EntitySpec.create(TestEntity.class));
        Entities.startManagement(startable);
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEqualsIgnoringOrder(entity.getLocations(), ImmutableSet.of(loc1, loc2));
        assertEqualsIgnoringOrder(entity2.getLocations(), ImmutableSet.of(loc1, loc2));
    }
    
    @Test
    public void testAppliesFilterToEntities() throws Exception {
        final List<Object> contexts = Lists.newCopyOnWriteArrayList();
        
        LocationsFilter filter = new LocationsFilter() {
            @Override public List<Location> filterForContext(List<Location> locations, Object context) {
                contexts.add(context);
                assertEquals(locations, ImmutableList.of(loc1, loc2));
                if (context instanceof Entity) {
                    String entityName = ((Entity)context).getDisplayName();
                    if ("1".equals(entityName)) {
                        return ImmutableList.<Location>of(loc1);
                    } else if ("2".equals(entityName)) {
                        return ImmutableList.<Location>of(loc2);
                    } else {
                        return ImmutableList.<Location>of();
                    }
                } else {
                    return ImmutableList.<Location>of();
                }
            }
        };
        startable = app.addChild(EntitySpec.create(BasicStartable.class)
                .configure(BasicStartable.LOCATIONS_FILTER, filter));
        entity = startable.addChild(EntitySpec.create(TestEntity.class).displayName("1"));
        entity2 = startable.addChild(EntitySpec.create(TestEntity.class).displayName("2"));
        Entities.startManagement(startable);
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEqualsIgnoringOrder(entity.getLocations(), ImmutableSet.of(loc1));
        assertEqualsIgnoringOrder(entity2.getLocations(), ImmutableSet.of(loc2));
        assertEqualsIgnoringOrder(contexts, ImmutableList.of(entity, entity2));
    }
    
    @Test
    public void testIgnoresUnstartableEntities() throws Exception {
        final AtomicReference<Exception> called = new AtomicReference<Exception>();
        LocationsFilter filter = new LocationsFilter() {
            @Override public List<Location> filterForContext(List<Location> locations, Object context) {
                called.set(new Exception());
                return locations;
            }
        };
        startable = app.addChild(EntitySpec.create(BasicStartable.class)
                .configure(BasicStartable.LOCATIONS_FILTER, filter));
        BasicEntity entity = startable.addChild(EntitySpec.create(BasicEntity.class));
        Entities.startManagement(startable);
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEqualsIgnoringOrder(entity.getLocations(), ImmutableSet.of());
        assertNull(called.get());
    }
    
    private void assertEqualsIgnoringOrder(Iterable<? extends Object> col1, Iterable<? extends Object> col2) {
        assertEquals(Iterables.size(col1), Iterables.size(col2), "col2="+col1+"; col2="+col2);
        assertEquals(MutableSet.copyOf(col1), MutableSet.copyOf(col2), "col1="+col1+"; col2="+col2);
    }
}
