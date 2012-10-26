package brooklyn.entity.basic;

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

public class EffectorBasicTest {

    // NB: more test of effector in EffectorSayHiTest and EffectorConcatenateTest
    // as well as EntityConfigMapUsageTest and others
    
    @Test
    public void testInvokeEffectorStart() {
        TestApplication app = new TestApplication();
        app.startManagement();
        List<SimulatedLocation> l = Arrays.asList(new SimulatedLocation());
        app.start(l);
        TestUtils.assertSetsEqual(l, app.getLocations());
    }

    @Test
    public void testInvokeEffectorStartWithTwoEntities() {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity(app);
        TestEntity entity2 = new TestEntity(app);
        app.startManagement();
        List<SimulatedLocation> l = Arrays.asList(new SimulatedLocation());
        app.start(l);
        TestUtils.assertSetsEqual(l, app.getLocations());
        TestUtils.assertSetsEqual(l, entity.getLocations());
        TestUtils.assertSetsEqual(l, entity2.getLocations());
    }
    
}
