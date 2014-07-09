package brooklyn.entity.basic;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigMapTest.MyOtherEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.IntegerAttributeSensorAndConfigKey;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;

/**
 * There is a bug where:
 *    class XI extends SI implements X
 *    class SI implements S  
 *    interface X extends Y
 *    config C is declared on S and overwritten at Y
 */
public class ConfigEntityInheritanceTest {

    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new TestApplicationImpl();
        Entities.startManagement(app);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    protected void checkKeys(Entity entity2, Integer value) {
        Assert.assertEquals(entity2.getConfig(MyOtherEntity.INT_KEY), value);
        Assert.assertEquals(entity2.getConfig(MyOtherEntity.SENSOR_AND_CONFIG_KEY), value);
    }

    @Test
    public void testConfigKeysIncludesHasConfigKeys() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MyOtherEntity.class)), 1);
    }
    
    @Test
    public void testConfigKeysIncludesHasConfigKeysInheritsOverwritten() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MyOtherEntityOverwriting.class)), 2);
    }
    @Test
    public void testConfigKeysIncludesHasConfigKeysInheritsOverwrittenThenInherited() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MyOtherEntityOverwritingThenInheriting.class)), 2);
    }
    
    public static class MyOtherEntityOverwriting extends MyOtherEntity {
        public static final ConfigKey<Integer> INT_KEY = ConfigKeys.newConfigKeyWithDefault(MyOtherEntity.INT_KEY, 2);
        public static final IntegerAttributeSensorAndConfigKey SENSOR_AND_CONFIG_KEY = 
                new IntegerAttributeSensorAndConfigKey(MyOtherEntity.SENSOR_AND_CONFIG_KEY, 2);
    }
    public static class MyOtherEntityOverwritingThenInheriting extends MyOtherEntityOverwriting {
    }

    // --------------------
    
    @Test
    public void testConfigKeysHere() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MyEntityHere.class)), 3);
    }
    @Test
    public void testConfigKeysSub() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MySubEntityHere.class)), 4);
    }
    @Test
    public void testConfigKeysSubExtended() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MySubEntityHere.class)), 4);
    }
    @Test
    public void testConfigKeysSubInheriting() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MySubEntityHereInheriting.class)), 4);
    }
    @Test
    public void testConfigKeysHereSubRight() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MySubEntityHereLeft.class)), 4);
    }
    @Test
    public void testConfigKeysSubLeft() throws Exception {
        checkKeys(app.addChild(EntitySpec.create(MySubEntityHereRight.class)), 4);
    }
    @Test
    public void testConfigKeysExtAndImplIntTwoRight() throws Exception {
        // this mirrors the bug observed in kafka entities;
        // the right-side interface normally dominates, but not when it is transitive
        // (although we shouldn't rely on order in any case;
        // new routines check whether one config key extends another and if so it takes the extending one)
        checkKeys(app.addChild(EntitySpec.create(MyEntityHereExtendingAndImplementingInterfaceImplementingTwoRight.class)), 4);
    }

    public interface MyInterfaceDeclaring {
        public static final ConfigKey<Integer> INT_KEY = 
            ConfigKeys.newIntegerConfigKey("intKey", "int key", 3);
        public static final AttributeSensorAndConfigKey<Integer,Integer> SENSOR_AND_CONFIG_KEY = 
            new IntegerAttributeSensorAndConfigKey("sensorConfigKey", "sensor+config key", 3);
    }
    public interface MyInterfaceRedeclaringAndInheriting extends MyInterfaceDeclaring {
        public static final ConfigKey<Integer> INT_KEY = ConfigKeys.newConfigKeyWithDefault(MyInterfaceDeclaring.INT_KEY, 4);
        public static final IntegerAttributeSensorAndConfigKey SENSOR_AND_CONFIG_KEY = 
                new IntegerAttributeSensorAndConfigKey(MyInterfaceDeclaring.SENSOR_AND_CONFIG_KEY, 4);
    }

    public interface MyInterfaceRedeclaring {
        public static final ConfigKey<Integer> INT_KEY = ConfigKeys.newConfigKeyWithDefault(MyInterfaceDeclaring.INT_KEY, 4);
        public static final IntegerAttributeSensorAndConfigKey SENSOR_AND_CONFIG_KEY = 
                new IntegerAttributeSensorAndConfigKey(MyInterfaceDeclaring.SENSOR_AND_CONFIG_KEY, 4);
    }
    
    public interface MyInterfaceRedeclaringThenExtending extends MyInterfaceRedeclaring {
    }

    public interface MyInterfaceExtendingLeft extends MyInterfaceRedeclaring, MyInterfaceDeclaring {
    }

    public interface MyInterfaceExtendingRight extends MyInterfaceDeclaring, MyInterfaceRedeclaring {
    }

    public static class MyEntityHere extends AbstractEntity implements MyInterfaceDeclaring {
    }
    
    public static class MySubEntityHere extends MyEntityHere implements MyInterfaceRedeclaring {
    }

    public static class MySubEntityHereInheriting extends MyEntityHere implements MyInterfaceRedeclaringAndInheriting {
    }

    public static class MySubEntityHereExtended extends MyEntityHere implements MyInterfaceRedeclaringThenExtending {
    }

    public static class MySubEntityHereLeft extends MyEntityHere implements MyInterfaceRedeclaring, MyInterfaceDeclaring {
    }

    public static class MySubEntityHereRight extends MyEntityHere implements MyInterfaceDeclaring, MyInterfaceRedeclaring {
    }
    
    public static class MyEntityHereExtendingAndImplementingInterfaceImplementingTwoRight extends MyEntityHere implements MyInterfaceExtendingRight {
    }

}
