package brooklyn.entity.basic

import static org.testng.Assert.*

import java.util.Map

import org.testng.annotations.Test

import brooklyn.entity.ConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.test.location.MockLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

/**
 * Test that configuration properties are usable and inherited correctly.
 */
public class InheritedConfigTest {
    private BasicConfigKey akey = [ String, "akey", "a key"]
    private BasicConfigKey bkey = [ Integer, "bkey", "b key"]
    
    @Test
    public void testConfigPassedInAtConstructorIsAvailable() throws Exception {
        Map<ConfigKey,Object> conf = [:]
        conf.put(akey,"aval")
        conf.put(bkey,2)
        
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app, config:conf])
        
        assertEquals("aval", entity.getConfig(akey))
        assertEquals(2, entity.getConfig(bkey))
    }
    
    @Test
    public void testConfigCanBeSetOnEntity() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(akey, "aval")
        entity.setConfig(bkey, 2)
        
        assertEquals("aval", entity.getConfig(akey))
        assertEquals(2, entity.getConfig(bkey))
    }
    
    @Test
    public void testConfigInheritedFromParent() throws Exception {
        Map<ConfigKey,Object> appConf = [:]
        appConf.put(akey,"aval")
        
        TestApplication app = new TestApplication([config:appConf]);
        app.setConfig(bkey, 2)
        TestEntity entity = new TestEntity([owner:app])
        
        assertEquals("aval", entity.getConfig(akey))
        assertEquals(2, entity.getConfig(bkey))
    }
    
    @Test
    public void testConfigInConstructorOverridesParentValue() throws Exception {
        Map<ConfigKey,Object> appConf = [:]
        appConf.put(akey,"aval")
        
        Map<ConfigKey,Object> childConf = [:]
        childConf.put(akey,"diffval")
        
        TestApplication app = new TestApplication([config:appConf]);
        TestEntity entity = new TestEntity([owner:app, config:childConf])
        
        assertEquals("diffval", entity.getConfig(akey))
    }
    
    @Test
    public void testConfigSetterOverridesParentValue() throws Exception {
        Map<ConfigKey,Object> appConf = [:]
        appConf.put(akey,"aval")
        
        TestApplication app = new TestApplication([config:appConf]);
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(akey, "diffval")
        
        assertEquals("diffval", entity.getConfig(akey))
    }
    
    @Test
    public void testConfigSetterOverridesConstructorValue() throws Exception {
        Map<ConfigKey,Object> childConf = [:]
        childConf.put(akey,"aval")
        
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app, config:childConf])
        entity.setConfig(akey, "diffval")
        
        assertEquals("diffval", entity.getConfig(akey))
    }

    @Test
    public void testConfigSetOnParentInheritedByExistingChildren() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        app.setConfig(akey,"aval")
        
        assertEquals("aval", entity.getConfig(akey))
    }

    @Test
    public void testConfigInheritedThroughManyGenerations() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity e = new TestEntity([owner:app])
        TestEntity e2 = new TestEntity([owner:e])
        app.setConfig(akey,"aval")
        
        assertEquals("aval", app.getConfig(akey))
        assertEquals("aval", e.getConfig(akey))
        assertEquals("aval", e2.getConfig(akey))
    }

    @Test
    public void testConfigCannotBeSetAfterApplicationIsStarted() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        app.start([new MockLocation()])
        
        try {
            app.setConfig(akey,"aval")
            fail();
        } catch (IllegalStateException e) {
            // success
        }
        
        assertEquals(null, entity.getConfig(akey))
    }
}
