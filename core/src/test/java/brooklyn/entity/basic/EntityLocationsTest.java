package brooklyn.entity.basic;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;

public class EntityLocationsTest {

    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testDuplicateLocationOnlyAddedOnce() {
        Location l = new SimulatedLocation();
        app.addLocations(Arrays.asList(l, l));
        app.addLocations(Arrays.asList(l, l));
        Assert.assertEquals(app.getLocations().size(), 1);
    }
    
}
