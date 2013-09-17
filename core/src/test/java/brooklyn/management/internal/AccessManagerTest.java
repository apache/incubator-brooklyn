package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableSet;

public class AccessManagerTest {

    private LocalManagementContext managementContext;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
        app = null;
    }

    @Test
    public void testEntityManagementAllowed() throws Exception {
        // default is allowed
        TestEntity e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        // when forbidden, should give error trying to create+manage new entity
        managementContext.getAccessManager().setEntityManagementAllowed(false);
        try {
            app.createAndManageChild(EntitySpec.create(TestEntity.class));
            fail();
        } catch (Exception e) {
            // expect it to be forbidden
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) {
                throw e;
            }
        }

        // when forbidden, should refuse to create new app
        try {
            ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
            fail();
        } catch (Exception e) {
            // expect it to be forbidden
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) {
                throw e;
            }
        }

        // but when forbidden, still allowed to create locations
        managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        
        // when re-enabled, can create entities again
        managementContext.getAccessManager().setEntityManagementAllowed(true);
        TestEntity e3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        assertEquals(ImmutableSet.copyOf(managementContext.getEntityManager().getEntities()), ImmutableSet.of(app, e1, e3));
    }
    
    @Test
    public void testLocationManagementAllowed() throws Exception {
        // default is allowed
        Location loc1 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));

        // when forbidden, should give error
        managementContext.getAccessManager().setLocationManagementAllowed(false);
        try {
            managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
            fail();
        } catch (Exception e) {
            // expect it to be forbidden
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) {
                throw e;
            }
        }

        // but when forbidden, still allowed to create entity
        ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        
        // when re-enabled, can create entities again
        managementContext.getAccessManager().setLocationManagementAllowed(true);
        Location loc3 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        
        assertEquals(ImmutableSet.copyOf(managementContext.getLocationManager().getLocations()), ImmutableSet.of(loc1, loc3));
    }
    
    @Test
    public void testLocationProvisioningAllowed() throws Exception {
        SimulatedLocation loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        
        // default is allowed
        assertTrue(managementContext.getAccessController().canProvisionLocation(loc).isAllowed());

        // when forbidden, should say so
        managementContext.getAccessManager().setLocationProvisioningAllowed(false);
        assertFalse(managementContext.getAccessController().canProvisionLocation(loc).isAllowed());

        // but when forbidden, still allowed to create locations
        managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        
        // when re-enabled, can create entities again
        managementContext.getAccessManager().setLocationProvisioningAllowed(true);
        assertTrue(managementContext.getAccessController().canProvisionLocation(loc).isAllowed());
    }
}
