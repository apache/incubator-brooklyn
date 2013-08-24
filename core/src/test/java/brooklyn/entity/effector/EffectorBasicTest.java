package brooklyn.entity.effector;

import java.util.List;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class EffectorBasicTest {

    private static final Logger log = LoggerFactory.getLogger(EffectorBasicTest.class);
    
    // NB: more tests of effectors in EffectorSayHiTest and EffectorConcatenateTest
    // as well as EntityConfigMapUsageTest and others

    private TestApplication app;
    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        locs = ImmutableList.of(new SimulatedLocation());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
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
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(locs);
        TestUtils.assertSetsEqual(locs, app.getLocations());
        TestUtils.assertSetsEqual(locs, entity.getLocations());
        TestUtils.assertSetsEqual(locs, entity2.getLocations());
    }
    
    @Test
    public void testInvokeEffectorTaskHasTag() {
        Task<Void> starting = app.invoke(Startable.START, MutableMap.of("locations", locs));
//        log.info("TAGS: "+starting.getTags());
        Assert.assertTrue(starting.getTags().contains(ManagementContextInternal.EFFECTOR_TAG));
    }
    
}
