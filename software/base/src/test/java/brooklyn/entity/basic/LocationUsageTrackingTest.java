package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.LocationUsage;
import brooklyn.location.basic.LocationUsage.LocationEvent;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class LocationUsageTrackingTest {

    private LocalManagementContext managementContext;
    private DynamicLocalhostMachineProvisioningLocation loc;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        managementContext = new LocalManagementContext();
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(DynamicLocalhostMachineProvisioningLocation.class));
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testUsageInitiallyEmpty() {
        Set<LocationUsage> usage = managementContext.getLocationManager().getLocationUsage(Predicates.alwaysTrue());
        assertEquals(usage, ImmutableSet.of());
    }

    @Test
    public void testUsageIncludesStartAndStopEvents() {
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        
        // Start the app; expect record of location in use
        long preStart = System.currentTimeMillis();
        app.start(ImmutableList.of(loc));
        long postStart = System.currentTimeMillis();
        SshMachineLocation machine = Iterables.getOnlyElement(loc.getAllMachines());
        
        Set<LocationUsage> usages1 = managementContext.getLocationManager().getLocationUsage(Predicates.alwaysTrue());
        LocationUsage usage1 = Iterables.getOnlyElement(usages1);
        List<LocationEvent> events1 = usage1.getEvents();
        LocationEvent event1 = Iterables.getOnlyElement(events1);
        
        assertEquals(usage1.getLocationId(), machine.getId());
        assertEquals(event1.getApplicationId(), app.getId());
        assertEquals(event1.getEntityId(), entity.getId());
        assertEquals(event1.getState(), Lifecycle.CREATED);
        assertTrue(event1.getDate().getTime() > preStart && event1.getDate().getTime() < postStart, "date="+event1.getDate()+"; pre="+preStart+"; post="+postStart);
        
        // Stop the app; expect record of location no longer in use
        long preStop = System.currentTimeMillis();
        app.stop();
        long postStop = System.currentTimeMillis();

        Set<LocationUsage> usages2 = managementContext.getLocationManager().getLocationUsage(Predicates.alwaysTrue());
        LocationUsage usage2 = Iterables.getOnlyElement(usages2);
        List<LocationEvent> events2 = usage2.getEvents();
        LocationEvent event2 = events2.get(1);

        assertEquals(events2.get(0).getDate(), event1.getDate());
        assertEquals(usage2.getLocationId(), machine.getId());
        assertEquals(event2.getApplicationId(), app.getId());
        assertEquals(event2.getEntityId(), entity.getId());
        assertEquals(event2.getState(), Lifecycle.DESTROYED);
        assertTrue(event2.getDate().getTime() > preStop && event2.getDate().getTime() < postStop, "date="+event2.getDate()+"; pre="+preStop+"; post="+postStop);
    }
    
    public static class DynamicLocalhostMachineProvisioningLocation extends LocalhostMachineProvisioningLocation {
        @Override
        public SshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
            System.out.println("called DynamicLocalhostMachineProvisioningLocation.obtain");
            return super.obtain(flags);
        }
        
        @Override
        public void release(SshMachineLocation machine) {
            System.out.println("called DynamicLocalhostMachineProvisioningLocation.release");
            super.release(machine);
            super.machines.remove(machine);
            super.removeChild(machine);
        }
    }
}
