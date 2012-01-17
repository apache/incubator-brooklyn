package brooklyn.entity.basic

import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestEntity

class AbstractApplicationTest {

    @Test
    public void testStartAndStopCallsChildren() {
        AbstractApplication app = new AbstractApplication() {};
        TestEntity child = new TestEntity(owner:app);
        
        app.start([new SimulatedLocation()])
        assertEquals(child.counter.get(), 1)
        
        app.stop()
        assertEquals(child.counter.get(), 0)
    }
}
