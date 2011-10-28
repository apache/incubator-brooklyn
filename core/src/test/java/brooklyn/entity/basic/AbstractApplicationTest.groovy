package brooklyn.entity.basic

import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.test.entity.TestEntity
import brooklyn.test.location.MockLocation

class AbstractApplicationTest {

    @Test
    public void testStartAndStopCallsChildren() {
        AbstractApplication app = new AbstractApplication() {};
        TestEntity child = new TestEntity(owner:app);
        
        app.start([new MockLocation()])
        assertEquals(child.counter.get(), 1)
        
        app.stop()
        assertEquals(child.counter.get(), 0)
    }
}
