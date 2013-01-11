package brooklyn.qa.performance

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.test.entity.TestEntityImpl

public class EntityPerformanceTest extends AbstractPerformanceTest {

    protected static final Logger LOG = LoggerFactory.getLogger(EntityPerformanceTest.class)
    
    private static final long TIMEOUT_MS = 10*1000
    
    TestEntity entity
    List<TestEntity> entities

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        super.setUp()
        
        entities = []
        for (int i = 0; i < 10; i++) {
            entities += new TestEntityImpl(parent:app)
        }
        entity = entities[0]
        
        app.start([loc])
    }
    
    protected int numIterations() {
        return 1000
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testGroovyNoopToEnsureTestFrameworkIsVeryFast() {
        int numIterations = numIterations()
        double minRatePerSec = 100000 * PERFORMANCE_EXPECTATION;
        int i = 0
        
        measureAndAssert("noop-groovy", numIterations, minRatePerSec) { i++ }
        assertTrue(i >= numIterations, "i=$i")
    }

    @Test(groups=["Integration", "Acceptance"])
    public void testUpdateAttributeWhenNoListeners() {
        int numIterations = numIterations()
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        int i = 0
        
        measureAndAssert("updateAttribute", numIterations, minRatePerSec) {
            entity.setAttribute(TestEntity.SEQUENCE, (i++))
        }
    }

    @Test(groups=["Integration", "Acceptance"])
    public void testUpdateAttributeWithNoopListeners() {
        int numIterations = numIterations()
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        int i = 0
        int lastVal = 0
        
        app.subscribe(entity, TestEntity.SEQUENCE, new SensorEventListener<Integer>() {
                @Override public void onEvent(SensorEvent<Integer> event) {
                    lastVal = event.value
                }});
        
        measureAndAssert("updateAttributeWithListener", numIterations, minRatePerSec) {
            entity.setAttribute(TestEntity.SEQUENCE, (++i))
        }
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertTrue(lastVal >= numIterations)
        }
    }

    @Test(groups=["Integration", "Acceptance"])
    public void testInvokeEffector() {
        int numIterations = numIterations()
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        measureAndAssert("invokeEffector", numIterations, minRatePerSec) {
            entity.myEffector()
        }
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testAsyncEffectorInvocation() {
        int numIterations = numIterations()
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        measureAndAssert("invokeEffectorAsyncAndGet", numIterations, minRatePerSec) {
            Task task = entity.invoke(TestEntity.MY_EFFECTOR)
            task.get()
        }
    }
    
    // TODO but surely parallel should be much faster?!
    @Test(groups=["Integration", "Acceptance"])
    public void testMultiEntityConcurrentEffectorInvocation() {
        int numIterations = numIterations()
        double minRatePerSec = 100 * PERFORMANCE_EXPECTATION; // i.e. 1000 invocations
        
        measureAndAssert("invokeEffectorMultiEntityConcurrentAsyncAndGet", numIterations, minRatePerSec) {
            Task task = Entities.invokeEffectorList(app, entities, TestEntity.MY_EFFECTOR)
            task.get()
        }
    }
}
