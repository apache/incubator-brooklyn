package brooklyn.entity.basic

import static org.testng.Assert.*

import java.util.Map
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch

import org.testng.annotations.Test

import brooklyn.entity.ConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.DependentConfiguration
import brooklyn.test.location.MockLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.test.location.MockLocation

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
    
    @Test
    public void testConfigReturnsDefaultValueIfNotSet() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "defaultval")
    }
    
    @Test
    public void testGetFutureConfigWhenReady() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.whenDone( {return "aval"} as Callable))
        app.start([new MockLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval")
    }
    
    @Test
    public void testGetFutureConfigBlocksUntilReady() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        final CountDownLatch latch = new CountDownLatch(1)
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.whenDone( {latch.await(); return "aval"} as Callable))
        app.start([new MockLocation()])
        
        Thread t = new Thread( { Thread.sleep(10); latch.countDown() } )
        try {
            long starttime = System.currentTimeMillis()
            t.start()
            assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval")
            long endtime = System.currentTimeMillis()
            
            assertTrue((endtime - starttime) > 10, "starttime=$starttime; endtime=$endtime")
            
        } finally {
            t.interrupt()
        }
    }
    
    @Test
    public void testGetAttributeWhenReadyConfigReturnsWhenSet() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        TestEntity entity2 = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity2, TestEntity.NAME))
        app.start([new MockLocation()])
        
        entity2.setAttribute(TestEntity.NAME, "aval")
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval")
    }
    
    @Test
    public void testGetAttributeWhenReadyConfigBlocksUntilSet() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        TestEntity entity2 = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity2, TestEntity.NAME))
        app.start([new MockLocation()])
        
        Thread t = new Thread( { Thread.sleep(10); entity2.setAttribute(TestEntity.NAME, "aval") } )
        try {
            long starttime = System.currentTimeMillis()
            t.start()
            assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval")
            long endtime = System.currentTimeMillis()
            
            assertTrue((endtime - starttime) > 10, "starttime=$starttime; endtime=$endtime")
            
        } finally {
            t.interrupt()
        }
    }

    @Test    
    public void testMapConfigKeyCanStoreAndRetrieveVals() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), "bval")
        app.start([new MockLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }
    
    @Test
    public void testMapConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start([new MockLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test    
    public void testListConfigKeyCanStoreAndRetrieveVals() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "bval")
        app.start([new MockLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }
    
    @Test
    public void testListConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start([new MockLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }
}
