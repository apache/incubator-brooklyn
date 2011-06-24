package brooklyn.entity.basic

import java.util.Map

import org.junit.Assert
import org.junit.Test

import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfigKey

class InheritedConfigTest {

    private BasicConfigKey akey = ["akey", String.class, "a key"]
    private BasicConfigKey bkey = ["bkey", Integer.class, "b key"]
    
    @Test
    public void testConfigPassedInAtConstructorIsAvailable() throws Exception {
        Map<ConfigKey,Object> conf = [:]
        conf.put(akey,"aval")
        conf.put(bkey,2)
        
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app, inheritableConfig:conf])
        
        Assert.assertEquals("aval", entity.getConfig(akey))
        Assert.assertEquals(2, entity.getConfig(bkey))
    }
    
    @Test
    public void testConfigCanBeSetOnEntity() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(akey, "aval")
        entity.setConfig(bkey, 2)
        
        Assert.assertEquals("aval", entity.getConfig(akey))
        Assert.assertEquals(2, entity.getConfig(bkey))
    }
    
    @Test
    public void testConfigInheritedFromParent() throws Exception {
        Map<ConfigKey,Object> appConf = [:]
        appConf.put(akey,"aval")
        
        TestApplication app = new TestApplication([inheritableConfig:appConf]);
        app.setConfig(bkey, 2)
        TestEntity entity = new TestEntity([owner:app])
        
        Assert.assertEquals("aval", entity.getConfig(akey))
        Assert.assertEquals(2, entity.getConfig(bkey))
    }
    
    @Test
    public void testConfigInConstructorOverridesParentValue() throws Exception {
        Map<ConfigKey,Object> appConf = [:]
        appConf.put(akey,"aval")
        
        Map<ConfigKey,Object> childConf = [:]
        childConf.put(akey,"diffval")
        
        TestApplication app = new TestApplication([inheritableConfig:appConf]);
        TestEntity entity = new TestEntity([owner:app, inheritableConfig:childConf])
        
        Assert.assertEquals("diffval", entity.getConfig(akey))
    }
    
    @Test
    public void testConfigSetterOverridesParentValue() throws Exception {
        Map<ConfigKey,Object> appConf = [:]
        appConf.put(akey,"aval")
        
        TestApplication app = new TestApplication([inheritableConfig:appConf]);
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(akey, "diffval")
        
        Assert.assertEquals("diffval", entity.getConfig(akey))
    }
    
    @Test
    public void testConfigSetterOverridesConstructorValue() throws Exception {
        Map<ConfigKey,Object> childConf = [:]
        childConf.put(akey,"aval")
        
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app, inheritableConfig:childConf])
        entity.setConfig(akey, "diffval")
        
        Assert.assertEquals("diffval", entity.getConfig(akey))
    }
    
    private static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }
    
    private static class TestEntity extends AbstractEntity {
        public TestEntity(Map properties=[:]) {
            super(properties)
        }
    }
}
