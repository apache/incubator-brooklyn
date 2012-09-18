package brooklyn.entity.basic

import static org.testng.Assert.assertEquals
import groovy.transform.InheritConstructors

import org.testng.Assert;
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.ConfigMap;
import brooklyn.event.basic.BasicConfigKey
import brooklyn.test.entity.TestApplication

import com.google.common.collect.ImmutableSet

class ConfigMapTest {

    private TestApplication app
    private MySubEntity entity

    @BeforeMethod
    public void setUp() {
        app = new TestApplication()
        entity = new MySubEntity(owner:app)
    }
    
    @Test
    public void testGetConfigKeysReturnsFromSuperAndInterfacesAndSubClass() throws Exception {
        assertEquals(entity.getEntityType().getConfigKeys(), ImmutableSet.of(
                MySubEntity.SUPER_KEY_1, MySubEntity.SUPER_KEY_2, MySubEntity.SUB_KEY_2, MySubEntity.INTERFACE_KEY_1));
    }

    @Test
    public void testConfigKeyDefaultUsesValueInSubClass() throws Exception {
        assertEquals(entity.getConfig(MyBaseEntity.SUPER_KEY_1), "overridden superKey1 default");
    }

    @Test
    public void testConfigureFromKey() throws Exception {
        MySubEntity entity2 = new MySubEntity((MySubEntity.SUPER_KEY_1): "changed", app);
        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed")
    }

    @Test
    public void testConfigureFromSuperKey() throws Exception {
        MySubEntity entity2 = new MySubEntity((MyBaseEntity.SUPER_KEY_1): "changed", app);
        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed")
    }
    
    @Test
    public void testConfigSubMap() throws Exception {
        entity.configure(MyBaseEntity.SUPER_KEY_1, "s1");
        entity.configure(MySubEntity.SUB_KEY_2, "s2");
        ConfigMap sub = entity.getConfigMap().submapMatchingGlob("sup*");
        Assert.assertEquals(sub.getRawConfig(MyBaseEntity.SUPER_KEY_1), "s1");
        Assert.assertNull(sub.getRawConfig(MySubEntity.SUB_KEY_2));
    }


    @InheritConstructors
    public static class MyBaseEntity extends AbstractEntity {
        public static final BasicConfigKey SUPER_KEY_1 = [ String, "superKey1", "superKey1 key", "superKey1 default"]
        public static final BasicConfigKey SUPER_KEY_2 = [ String, "superKey2", "superKey2 key", "superKey2 default"]
    }
    
    @InheritConstructors
    public static class MySubEntity extends MyBaseEntity implements MyInterface {
        public static final BasicConfigKey SUPER_KEY_1 = [ MyBaseEntity.SUPER_KEY_1, "overridden superKey1 default"]
        public static final BasicConfigKey SUB_KEY_2 = [ String, "subKey2", "subKey2 key", "subKey2 default"]
    }
    
    public interface MyInterface {
        public static final BasicConfigKey INTERFACE_KEY_1 = [ String, "interfaceKey1", "interface key 1", "interfaceKey1 default"]
    }
}
