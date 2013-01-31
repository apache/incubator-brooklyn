package brooklyn.entity.basic;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;

public class EffectorBasicTest {

    // NB: more test of effector in EffectorSayHiTest and EffectorConcatenateTest
    // as well as EntityConfigMapUsageTest and others

    private TestApplication app;
    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        locs = ImmutableList.of(new SimulatedLocation());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }
    
    @Test
    public void testInvokeEffectorStart() {
        app.start(locs);
        TestUtils.assertSetsEqual(locs, app.getLocations());
        // TODO above does not get registered as a task
    }

    @Test
    public void testInvokeEffectorStartWithMap() {
        app.invoke(Startable.START, MutableMap.of("locations", locs)).getUnchecked();
        TestUtils.assertSetsEqual(locs, app.getLocations());
    }

    @Test
    public void testInvokeEffectorStartWithArgs() {
        Entities.invokeEffectorWithArgs((EntityLocal)app, app, Startable.START, locs).getUnchecked();
        TestUtils.assertSetsEqual(locs, app.getLocations());
    }


    @Test
    public void testInvokeEffectorStartWithTwoEntities() {
        TestEntity entity = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        TestEntity entity2 = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        app.start(locs);
        TestUtils.assertSetsEqual(locs, app.getLocations());
        TestUtils.assertSetsEqual(locs, entity.getLocations());
        TestUtils.assertSetsEqual(locs, entity2.getLocations());
    }
}
