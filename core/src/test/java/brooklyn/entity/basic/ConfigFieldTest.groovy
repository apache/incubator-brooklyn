package brooklyn.entity.basic

import static org.testng.Assert.assertEquals
import groovy.transform.InheritConstructors

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.event.basic.BasicConfigKey
import brooklyn.test.entity.TestApplication

import com.google.common.collect.ImmutableSet

class ConfigFieldTest {

    private TestApplication app
    private MySubEntity entity

    @BeforeMethod
    public void setUp() {
        app = new TestApplication()
        entity = new MySubEntity(owner:app)
    }
    
    @Test
    public void testGetConfigKeysReturnsFromSuperAndInterfacesAndSubClass() throws Exception {
        assertEquals(entity.configKeys.keySet(), ImmutableSet.of("superKey1", "superKey2", "subKey2", "interfaceKey1"))
    }

    @Test
    public void testConfigKeyDefaultUsesValueInSubClass() throws Exception {
        assertEquals(entity.getConfig(MyBaseEntity.SUPER_KEY_1), "overridden superKey1 default")
    }

    @Test
    public void testConfigureFromKey() throws Exception {
        MySubEntity entity2 = new MySubEntity((MySubEntity.SUPER_KEY_1): "changed", app);
        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed")
    }

    //FIXME config needs to be mapped based on string value, not the key itself
//    @Test
//    public void testConfigureFromSuperKey() throws Exception {
//        MySubEntity entity2 = new MySubEntity((MyBaseEntity.SUPER_KEY_1): "changed", app);
//        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed")
//    }

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
        BasicConfigKey INTERFACE_KEY_1 = [ String, "interfaceKey1", "interface key 1", "interfaceKey1 default"]
    }
}
