package brooklyn.qa.longevity;

import org.testng.annotations.Test;

import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.util.javalang.JavaClassNames;

/**
 * This test is NOT definitive because GC is not guaranteed.
 */
public class EntityCleanupLongevityTest extends EntityCleanupLongevityTestFixture {

    @Override
    protected int numIterations() {
        return 10*1000;
    }
    
    @Override
    protected boolean checkMemoryLeaks() {
        return true;
    }

    @Test(groups={"Longevity","Acceptance"})
    public void testAppCreatedStartedAndStopped() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), true);
    }
    
    @Test(groups={"Longevity","Acceptance"})
    public void testAppCreatedStartedAndUnmanaged() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), false);
    }

    @Test(groups={"Longevity","Acceptance"})
    public void testLocationCreatedAndUnmanaged() throws Exception {
        doTestManyTimesAndAssertNoMemoryLeak(JavaClassNames.niceClassAndMethod(), new Runnable() {
            public void run() {
                loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
                managementContext.getLocationManager().unmanage(loc);
            }
        });
    }
    
}
