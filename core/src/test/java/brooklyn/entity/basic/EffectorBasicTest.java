package brooklyn.entity.basic;

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.MutableMap;

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
        // TODO above does not get registered as a task
    }

    @Test
    public void testInvokeEffectorStartWithMap() {
        TestApplication app = new TestApplication();
        app.startManagement();
        List<SimulatedLocation> l = Arrays.asList(new SimulatedLocation());
        app.invoke(Startable.START, MutableMap.of("locations", l)).getUnchecked();
        TestUtils.assertSetsEqual(l, app.getLocations());
    }

    @Test
    public void testInvokeEffectorStartWithArgs() {
        TestApplication app = new TestApplication();
        app.startManagement();
        List<SimulatedLocation> l = Arrays.asList(new SimulatedLocation());
        Entities.invokeEffectorWithArgs(app, app, Startable.START, l).getUnchecked();
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
