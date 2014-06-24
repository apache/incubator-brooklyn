package brooklyn.qa.performance;

import org.testng.annotations.Test;


@Test(groups={"Integration", "Acceptance"})
public class EntityPerformanceLongevityTest extends EntityPerformanceTest {

    // TODO enable this to some big number to see what happens when things run for a long time.
    // e.g. will we eventually get OOME when storing all tasks relating to effector calls?

//    @Override
//    protected int numIterations() {
//        return 1000000;
//    }
    
}
