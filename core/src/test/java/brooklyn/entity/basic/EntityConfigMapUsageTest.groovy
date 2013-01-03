package brooklyn.entity.basic

import static org.testng.Assert.*

import java.util.Map
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.DependentConfiguration
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

/**
 * Test that configuration properties are usable and inherited correctly.
 */
public class EntityConfigMapUsageTest {
    private BasicConfigKey intKey = [ Integer, "bkey", "b key"]
    private BasicConfigKey strKey = [ String, "akey", "a key"]
    private BasicConfigKey intKeyWithDefault = [ Integer, "ckey", "c key", 1]
    private BasicConfigKey strKeyWithDefault = [ String, "strKey", "str key", "str key default"]
    
    private TestApplication app;
    
    @BeforeMethod
    public void setUp() {
        app = new TestApplication();
    }
    
    @Test
    public void testConfigPassedInAtConstructorIsAvailable() throws Exception {
        Map<ConfigKey,Object> conf = [:]
        conf.put(strKey,"aval")
        conf.put(intKey,2)
        
        TestEntity entity = new TestEntity([parent:app, config:conf])
        
        assertEquals("aval", entity.getConfig(strKey))
        assertEquals(2, entity.getConfig(intKey))
    }
    
    @Test
    public void testConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        TestEntity entity = new TestEntity([parent:app, config:[(intKeyWithDefault):0]])
        
        assertEquals(entity.getConfig(intKeyWithDefault), 0)
    }
    
    @Test
    public void testInheritedConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        TestEntity parent = new TestEntity([parent:app, config:[(intKeyWithDefault):0]])
        TestEntity entity = new TestEntity([parent:parent])
        
        assertEquals(entity.getConfig(intKeyWithDefault), 0)
    }
    
    @Test
    public void testConfigSetToNullIsAvailable() throws Exception {
        TestEntity entity = new TestEntity([parent:app, config:[(strKeyWithDefault):null]])
        
        assertEquals(entity.getConfig(strKeyWithDefault), null)
    }
    
    @Test
    public void testInheritedConfigSetToNullIsAvailable() throws Exception {
        TestEntity parent = new TestEntity([parent:app, config:[(strKeyWithDefault):null]])
        TestEntity entity = new TestEntity([parent:parent])
        
        assertEquals(entity.getConfig(strKeyWithDefault), null)
    }
    
    @Test
    public void testConfigCanBeSetOnEntity() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        entity.setConfig(strKey, "aval")
        entity.setConfig(intKey, 2)
        
        assertEquals("aval", entity.getConfig(strKey))
        assertEquals(2, entity.getConfig(intKey))
    }
    
    @Test
    public void testConfigInheritedFromParent() throws Exception {
        TestEntity parent = new TestEntity([parent:app, config:[(strKey):"aval"]])
        parent.setConfig(intKey, 2)
        TestEntity entity = new TestEntity([parent:parent])
        
        assertEquals("aval", entity.getConfig(strKey))
        assertEquals(2, entity.getConfig(intKey))
    }
    
    @Test
    public void testConfigInConstructorOverridesParentValue() throws Exception {
        TestEntity parent = new TestEntity([parent:app, config:[(strKey):"aval"]])
        TestEntity entity = new TestEntity([parent:parent, config:[(strKey):"diffval"]])
        
        assertEquals("diffval", entity.getConfig(strKey))
    }
    
    @Test
    public void testConfigSetterOverridesParentValue() throws Exception {
        Map<ConfigKey,Object> appConf = [:]
        appConf.put(strKey,"aval")
        
        TestEntity parent = new TestEntity([config:[(strKey):"aval"]]);
        TestEntity entity = new TestEntity([parent:parent])
        entity.setConfig(strKey, "diffval")
        
        assertEquals("diffval", entity.getConfig(strKey))
    }
    
    @Test
    public void testConfigSetterOverridesConstructorValue() throws Exception {
        TestEntity entity = new TestEntity([parent:app, config:[(strKey):"aval"]])
        entity.setConfig(strKey, "diffval")
        
        assertEquals("diffval", entity.getConfig(strKey))
    }

    @Test
    public void testConfigSetOnParentInheritedByExistingChildrenBeforeStarted() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        app.setConfig(strKey,"aval")
        
        assertEquals("aval", entity.getConfig(strKey))
    }

    @Test
    public void testConfigInheritedThroughManyGenerations() throws Exception {
        TestEntity e = new TestEntity([parent:app])
        TestEntity e2 = new TestEntity([parent:e])
        app.setConfig(strKey,"aval")
        
        assertEquals("aval", app.getConfig(strKey))
        assertEquals("aval", e.getConfig(strKey))
        assertEquals("aval", e2.getConfig(strKey))
    }

    @Test(enabled=false)
    public void testConfigCannotBeSetAfterApplicationIsStarted() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        app.start([new SimulatedLocation()])
        
        try {
            app.setConfig(strKey,"aval")
            fail();
        } catch (IllegalStateException e) {
            // success
        }
        
        assertEquals(null, entity.getConfig(strKey))
    }
    
    @Test
    public void testConfigReturnsDefaultValueIfNotSet() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "defaultval")
    }
    
    @Test
    public void testGetFutureConfigWhenReady() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.whenDone( {return "aval"} as Callable))
        app.start([new SimulatedLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval")
    }
    
    @Test
    public void testGetFutureConfigBlocksUntilReady() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        final CountDownLatch latch = new CountDownLatch(1)
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.whenDone( {latch.await(); return "aval"} as Callable))
        app.start([new SimulatedLocation()])
        
        Thread t = new Thread( { Thread.sleep(10); latch.countDown() } )
        try {
            long starttime = System.currentTimeMillis()
            t.start()
            assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval")
            long endtime = System.currentTimeMillis()
            
            assertTrue((endtime - starttime) >= 10, "starttime=$starttime; endtime=$endtime")
            
        } finally {
            t.interrupt()
        }
    }
    
    @Test
    public void testGetAttributeWhenReadyConfigReturnsWhenSet() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        TestEntity entity2 = new TestEntity([parent:app])
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity2, TestEntity.NAME))
        app.start([new SimulatedLocation()])
        
        entity2.setAttribute(TestEntity.NAME, "aval")
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval")
    }
    
    @Test
    public void testGetAttributeWhenReadyWithPostProcessingConfigReturnsWhenSet() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        TestEntity entity2 = new TestEntity([parent:app])
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributePostProcessedWhenReady(entity2, TestEntity.NAME, {it}, { it+"mysuffix"}))
        app.start([new SimulatedLocation()])
        
        entity2.setAttribute(TestEntity.NAME, "aval")
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "avalmysuffix")
    }
    
    @Test
    public void testGetAttributeWhenReadyConfigBlocksUntilSet() throws Exception {
        TestEntity entity = new TestEntity([parent:app])
        TestEntity entity2 = new TestEntity([parent:app])
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity2, TestEntity.NAME))
        app.start([new SimulatedLocation()])
        
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

}
