package brooklyn.qa.performance

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.test.entity.TestEntity


public class EntityPerformanceLongevityTest extends EntityPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(EntityPerformanceLongevityTest.class)

    // TODO enable this to some big number to see what happens when things run for a long time.
    // e.g. will we eventually get OOME when storing all tasks relating to effector calls?
    
//    protected int numIterations() {
//        return 1000000
//    }
    
}
