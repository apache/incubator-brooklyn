package brooklyn.qa.longevity;

import org.testng.annotations.Test;

import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.util.javalang.JavaClassNames;

public class EntityCleanupTest extends EntityCleanupLongevityTestFixture {

    @Override
    protected int numIterations() {
        return 1;
    }

    @Override
    protected boolean checkMemoryLeaks() {
        return false;
    }
    
    @Test
    public void testAppCreatedStartedAndStopped() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), true);
    }
    
    @Test
    public void testAppCreatedStartedAndUnmanaged() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), false);
    }

    @Test
    public void testLocationCreatedAndUnmanaged() throws Exception {
        doTestManyTimesAndAssertNoMemoryLeak(JavaClassNames.niceClassAndMethod(), new Runnable() {
            public void run() {
                loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
                managementContext.getLocationManager().unmanage(loc);
            }
        });
    }
    
}
