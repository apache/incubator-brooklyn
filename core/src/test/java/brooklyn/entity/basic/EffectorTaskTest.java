package brooklyn.entity.basic;

import java.util.concurrent.Callable;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.DynamicSequentialTask;
import brooklyn.util.task.DynamicTasks;

import com.google.common.collect.Iterables;

public class EffectorTaskTest {

    TestApplication app;
    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }

    // ----------- syntax 1 -- effector with body in a class
    
    public static final Effector<Integer> DOUBLE = Effectors.effector(Integer.class, "double")
            .description("doubles the given number")
            .parameter(Integer.class, "numberToDouble")
            .impl(new EffectorBody<Integer>() {
                public Integer main(ConfigBag parameters) {
                    // do a sanity check
                    Assert.assertNotNull(entity());
                    
                    // finally double the input
                    return 2*(Integer)parameters.getStringKey("numberToDouble");
                }
            })
            .build();

    public static class DoublingEntity extends AbstractEntity {
        private static final long serialVersionUID = -3006794991232529824L;
        public static final Effector<Integer> DOUBLE = EffectorTaskTest.DOUBLE;
    }

    @Test
    public void testSimpleEffector() throws Exception {
        Entity doubler = app.addChild(EntitySpecs.spec(DoublingEntity.class));
        Entities.manage(doubler);
        
        Assert.assertEquals(doubler.invoke(DOUBLE, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }

    
    // ----------------- syntax 2 -- an effector using subtasks
    
    public static Task<Integer> add(final int x, final int y) {
        return DynamicTasks.autoAddTask(
                new BasicTask<Integer>(new Callable<Integer>() { public Integer call() { return x+y; } })
                );
    }

    public static Task<Integer> add(final Task<Integer> x, final int y) {
        return DynamicTasks.autoAddTask(
                new BasicTask<Integer>(new Callable<Integer>() { public Integer call() { return x.getUnchecked()+y; } })
                );
    }

    public static Task<Integer> times(final int x, final int y) {
        return DynamicTasks.autoAddTask(
                new BasicTask<Integer>(new Callable<Integer>() { public Integer call() { return x*y; } })
                );
    }

    public static Task<Integer> times(final Task<Integer> x, final int y) {
        return DynamicTasks.autoAddTask(
                        new BasicTask<Integer>(new Callable<Integer>() { public Integer call() { return x.getUnchecked()*y; } })
                    );
    }
    
    public static final Effector<Integer> TWO_X_PLUS_ONE = Effectors.effector(Integer.class, "twoXPlusOne")
            .description("doubles the given number and adds one")
            .parameter(Integer.class, "numberToStartWith")
            .impl(new EffectorBody<Integer>() {
                public Integer main(ConfigBag parameters) {
                    int input = (Integer)parameters.getStringKey("numberToStartWith");
                    return add(times(input, 2), 1).getUnchecked();
                }
            })
            .build();

    public static class Txp1Entity extends AbstractEntity {
        private static final long serialVersionUID = 6732818057132953567L;
        public static final Effector<Integer> TWO_X_P_1 = EffectorTaskTest.TWO_X_PLUS_ONE;
    }

    /** the composed effector should allow us to inspect its children */
    @Test
    public void testComposedEffector() throws Exception {
        Entity txp1 = app.addChild(EntitySpecs.spec(Txp1Entity.class));
        Entities.manage(txp1);
        
        Task<Integer> e = txp1.invoke(TWO_X_PLUS_ONE, MutableMap.of("numberToStartWith", 3));
        Assert.assertTrue(e instanceof DynamicSequentialTask);
        Assert.assertEquals(e.get(), (Integer)7);
        Assert.assertEquals( Iterables.size( ((HasTaskChildren)e).getChildrenTasks() ), 2);
    }

    
//    // TODO dynamically added effector
    
//    @Test
//    public void testSimpleEffectorDynamicallyAdded() throws Exception {
//        Entity doubler = app.addChild(EntitySpecs.spec(TestEntity.class));
//        Entities.manage(doubler);
//        
//        boolean failed = false;
//        try {
//            doubler.invoke(DOUBLE, MutableMap.of("numberToDouble", 3));
//        } catch (Exception e) {
//            failed = true;
//        }
//        if (!failed) Assert.fail("doubling should have failed because it is not registered on the entity");
//        
//        doubler.addEffector(DOUBLE, XXX);
//        Assert.assertEquals(doubler.invoke(DOUBLE, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
//    }
//
    
}