package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Callables;

/**
 * Test that configuration properties are usable and inherited correctly.
 */
public class EntityConfigMapUsageTest {
    private static final int EARLY_RETURN_GRACE = 10;
    
    private BasicConfigKey<Integer> intKey = new BasicConfigKey<Integer>(Integer.class, "bkey", "b key");
    private ConfigKey<String> strKey = new BasicConfigKey<String>(String.class, "akey", "a key");
    private ConfigKey<Integer> intKeyWithDefault = new BasicConfigKey<Integer>(Integer.class, "ckey", "c key", 1);
    private ConfigKey<String> strKeyWithDefault = new BasicConfigKey<String>(String.class, "strKey", "str key", "str key default");
    
    private TestApplication app;
    private List<SimulatedLocation> locs;
    
    @BeforeMethod
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        locs = ImmutableList.of(new SimulatedLocation());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testConfigPassedInAtConstructionIsAvailable() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKey, "aval")
                .configure(intKey, 2));

        assertEquals(entity.getConfig(strKey), "aval");
        assertEquals(entity.getConfig(intKey), (Integer)2);
    }
    
    @Test
    public void testConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(intKeyWithDefault, 0));
        
        assertEquals(entity.getConfig(intKeyWithDefault), (Integer)0);
    }
    
    @Test
    public void testInheritedConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        TestEntity parent = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(intKeyWithDefault, 0));
        TestEntity entity = parent.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        assertEquals(entity.getConfig(intKeyWithDefault), (Integer)0);
    }
    
    @Test
    public void testConfigSetToNullIsAvailable() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKeyWithDefault, (String)null));
        
        assertEquals(entity.getConfig(strKeyWithDefault), null);
    }
    
    @Test
    public void testInheritedConfigSetToNullIsAvailable() throws Exception {
        TestEntity parent = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKeyWithDefault, (String)null));
        TestEntity entity = parent.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        assertEquals(entity.getConfig(strKeyWithDefault), null);
    }
    
    @Test
    public void testInheritedConfigAvailableDeepInHierarchy() throws Exception {
        TestEntity parent = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKeyWithDefault, "customval"));
        TestEntity entity = parent.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity2 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity3 = entity2.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        assertEquals(entity.getConfig(strKeyWithDefault), "customval");
        assertEquals(entity2.getConfig(strKeyWithDefault), "customval");
        assertEquals(entity3.getConfig(strKeyWithDefault), "customval");
    }
    
    @Test
    public void testConfigCanBeSetOnEntity() throws Exception {
        TestEntity entity = app.createChild(EntitySpec.create(TestEntity.class));
        ((EntityLocal)entity).setConfig(strKey, "aval");
        ((EntityLocal)entity).setConfig(intKey, 2);
        Entities.manage(entity);
        
        assertEquals(entity.getConfig(strKey), "aval");
        assertEquals(entity.getConfig(intKey), (Integer)2);
    }
    
    @Test
    public void testConfigInheritedFromParent() throws Exception {
        TestEntity parent = app.createChild(EntitySpec.create(TestEntity.class)
                .configure(strKey, "aval"));
        ((EntityLocal)parent).setConfig(intKey, 2);
        Entities.manage(parent);
        TestEntity entity = parent.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        assertEquals(entity.getConfig(strKey), "aval");
        assertEquals(2, entity.getConfig(intKey), (Integer)2);
    }
    
    @Test
    public void testConfigAtConstructionOverridesParentValue() throws Exception {
        TestEntity parent = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKey, "aval"));
        TestEntity entity = parent.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKey, "diffval"));
        
        assertEquals(entity.getConfig(strKey), "diffval");
    }
    
    @Test
    public void testConfigSetterOverridesParentValue() throws Exception {
        TestEntity parent = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKey, "aval"));
        TestEntity entity = parent.createAndManageChild(EntitySpec.create(TestEntity.class));
        ((EntityLocal)entity).setConfig(strKey, "diffval");
        
        assertEquals(entity.getConfig(strKey), "diffval");
    }
    
    @Test
    public void testConfigSetterOverridesConstructorValue() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKey, "aval"));
        ((EntityLocal)entity).setConfig(strKey, "diffval");
        Entities.manage(entity);
        
        assertEquals(entity.getConfig(strKey), "diffval");
    }

    @Test
    public void testConfigSetOnParentInheritedByExistingChildrenBeforeStarted() throws Exception {
        TestEntity parent = app.createChild(EntitySpec.create(TestEntity.class));
        TestEntity entity = parent.createChild(EntitySpec.create(TestEntity.class));
        ((EntityLocal)parent).setConfig(strKey,"aval");
        Entities.manage(entity);
        
        assertEquals(entity.getConfig(strKey), "aval");
    }

    @Test
    public void testConfigInheritedThroughManyGenerations() throws Exception {
        TestEntity e = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(strKey, "aval"));
        TestEntity e2 = e.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e3 = e2.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        assertEquals(e.getConfig(strKey), "aval");
        assertEquals(e2.getConfig(strKey), "aval");
        assertEquals(e3.getConfig(strKey), "aval");
    }

    // This has been relaxed to a warning, with a message saying "may not be supported in future versions"
    @Test(enabled=false)
    public void testConfigCannotBeSetAfterApplicationIsStarted() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(locs);
        
        try {
            ((EntityLocal)app).setConfig(strKey,"aval");
            fail();
        } catch (IllegalStateException e) {
            // success
        }
        
        assertEquals(entity.getConfig(strKey), null);
    }
    
    @Test
    public void testConfigReturnsDefaultValueIfNotSet() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "defaultval");
    }
    
    @Test
    public void testGetFutureConfigWhenReady() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, DependentConfiguration.whenDone(Callables.returning("aval"))));
        app.start(locs);
        
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval");
    }
    
    @Test
    public void testGetFutureConfigBlocksUntilReady() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, DependentConfiguration.whenDone(new Callable<String>() {
                        public String call() {
                            try {
                                latch.await(); return "aval";
                            } catch (InterruptedException e) {
                                throw Exceptions.propagate(e);
                            }
                        }})));
        app.start(locs);
        
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(10+EARLY_RETURN_GRACE); latch.countDown();
                    } catch (InterruptedException e) {
                        throw Exceptions.propagate(e);
                    }
                }});
        try {
            long starttime = System.currentTimeMillis();
            t.start();
            assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval");
            long endtime = System.currentTimeMillis();
            
            assertTrue((endtime - starttime) >= 10, "starttime="+starttime+"; endtime="+endtime);
            
        } finally {
            t.interrupt();
        }
    }
    
    @Test
    public void testGetAttributeWhenReadyConfigReturnsWhenSet() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME)));
        app.start(locs);
        
        ((EntityLocal)entity).setAttribute(TestEntity.NAME, "aval");
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "aval");
    }
    
    @Test
    public void testGetAttributeWhenReadyWithPostProcessingConfigReturnsWhenSet() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, DependentConfiguration.attributePostProcessedWhenReady(entity, TestEntity.NAME, Predicates.notNull(), new Function<String,String>() {
                        public String apply(String input) {
                            return input+"mysuffix";
                        }})));
        app.start(locs);
        
        ((EntityLocal)entity).setAttribute(TestEntity.NAME, "aval");
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "avalmysuffix");
    }
    
    @Test
    public void testGetAttributeWhenReadyConfigBlocksUntilSet() throws Exception {
        final TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME)));
        app.start(locs);
        
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(10+EARLY_RETURN_GRACE);
                    ((EntityLocal)entity).setAttribute(TestEntity.NAME, "aval");
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
            }});
        try {
            long starttime = System.currentTimeMillis();
            t.start();
            assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "aval");
            long endtime = System.currentTimeMillis();
            
            assertTrue((endtime - starttime) > 10, "starttime="+starttime+"; endtime="+endtime);
            
        } finally {
            t.interrupt();
        }
    }
}
