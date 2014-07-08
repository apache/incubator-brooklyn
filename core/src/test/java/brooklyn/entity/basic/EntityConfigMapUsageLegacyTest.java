/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Callables;

/**
 * Test that configuration properties are usable and inherited correctly.
 * 
 * Uses legacy mechanism of calling entity constructors.
 */
public class EntityConfigMapUsageLegacyTest extends BrooklynAppUnitTestSupport {
    private ConfigKey<Integer> intKey = ConfigKeys.newIntegerConfigKey("bkey", "b key");
    private ConfigKey<String> strKey = ConfigKeys.newStringConfigKey("akey", "a key");
    private ConfigKey<Integer> intKeyWithDefault = ConfigKeys.newIntegerConfigKey("ckey", "c key", 1);
    private ConfigKey<String> strKeyWithDefault = ConfigKeys.newStringConfigKey("strKey", "str key", "str key default");
    
    @Test
    public void testConfigPassedInAtConstructorIsAvailable() throws Exception {
        TestEntity entity = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKey, "aval", intKey, 2)), app);
        Entities.manage(entity);
        
        assertEquals(entity.getConfig(strKey), "aval");
        assertEquals(entity.getConfig(intKey), Integer.valueOf(2));
    }
    
    @Test
    public void testConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        TestEntity entity = new TestEntityImpl(MutableMap.of("config", MutableMap.of(intKeyWithDefault, 0)), app);
        Entities.manage(entity);
        
        assertEquals(entity.getConfig(intKeyWithDefault), (Integer)0);
    }
    
    @Test
    public void testInheritedConfigSetToGroovyTruthFalseIsAvailable() throws Exception {
        TestEntity parent = new TestEntityImpl(MutableMap.of("config", MutableMap.of(intKeyWithDefault, 0)), app);
        TestEntity entity = new TestEntityImpl(parent);
        Entities.manage(parent);
        
        assertEquals(entity.getConfig(intKeyWithDefault), (Integer)0);
    }
    
    @Test
    public void testConfigSetToNullIsAvailable() throws Exception {
        TestEntity entity = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKeyWithDefault, null)), app);
        Entities.manage(entity);
        
        assertEquals(entity.getConfig(strKeyWithDefault), null);
    }
    
    @Test
    public void testInheritedConfigSetToNullIsAvailable() throws Exception {
        TestEntity parent = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKeyWithDefault, null)), app);
        TestEntity entity = new TestEntityImpl(parent);
        Entities.manage(parent);

        assertEquals(entity.getConfig(strKeyWithDefault), null);
    }
    
    @Test
    public void testConfigCanBeSetOnEntity() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        entity.setConfig(strKey, "aval");
        entity.setConfig(intKey, 2);
        Entities.manage(entity);
        
        assertEquals(entity.getConfig(strKey), "aval");
        assertEquals(entity.getConfig(intKey), (Integer)2);
    }
    
    @Test
    public void testConfigInheritedFromParent() throws Exception {
        TestEntity parent = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKey, "aval")), app);
        parent.setConfig(intKey, 2);
        TestEntity entity = new TestEntityImpl(parent);
        Entities.manage(parent);
        
        assertEquals(entity.getConfig(strKey), "aval");
        assertEquals(entity.getConfig(intKey), (Integer)2);
    }
    
    @Test
    public void testConfigInConstructorOverridesParentValue() throws Exception {
        TestEntity parent = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKey, "aval")), app);
        TestEntity entity = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKey, "diffval")), parent);
        Entities.manage(parent);

        assertEquals("diffval", entity.getConfig(strKey));
    }
    
    @Test
    public void testConfigSetterOverridesParentValue() throws Exception {
        TestEntity parent = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKey, "aval")), app);
        TestEntity entity = new TestEntityImpl(parent);
        entity.setConfig(strKey, "diffval");
        Entities.manage(parent);
        
        assertEquals("diffval", entity.getConfig(strKey));
    }
    
    @Test
    public void testConfigSetterOverridesConstructorValue() throws Exception {
        TestEntity entity = new TestEntityImpl(MutableMap.of("config", MutableMap.of(strKey, "aval")), app);
        entity.setConfig(strKey, "diffval");
        Entities.manage(entity);
        
        assertEquals("diffval", entity.getConfig(strKey));
    }

    @Test
    public void testConfigSetOnParentInheritedByExistingChildrenBeforeStarted() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        app.setConfig(strKey,"aval");
        Entities.manage(entity);

        assertEquals("aval", entity.getConfig(strKey));
    }

    @Test
    public void testConfigInheritedThroughManyGenerations() throws Exception {
        TestEntity e = new TestEntityImpl(app);
        TestEntity e2 = new TestEntityImpl(e);
        app.setConfig(strKey,"aval");
        Entities.manage(e);

        assertEquals("aval", app.getConfig(strKey));
        assertEquals("aval", e.getConfig(strKey));
        assertEquals("aval", e2.getConfig(strKey));
    }

    @Test(enabled=false)
    public void testConfigCannotBeSetAfterApplicationIsStarted() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        Entities.manage(entity);
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        try {
            app.setConfig(strKey,"aval");
            fail();
        } catch (IllegalStateException e) {
            // success
        }
        
        assertEquals(null, entity.getConfig(strKey));
    }
    
    @Test
    public void testConfigReturnsDefaultValueIfNotSet() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        Entities.manage(entity);
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "defaultval");
    }
    
    @Test
    public void testGetFutureConfigWhenReady() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.whenDone(Callables.returning("aval")));
        Entities.manage(entity);
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval");
    }
    
    @Test
    public void testGetFutureConfigBlocksUntilReady() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        final CountDownLatch latch = new CountDownLatch(1);
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.whenDone(new Callable<String>() {
            @Override public String call() throws Exception {
                latch.await();
                return "aval";
            }}));
        Entities.manage(entity);
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        Thread t = new Thread(new Runnable() {
            public void run() {
                Time.sleep(10);
                latch.countDown();
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
        TestEntity entity = new TestEntityImpl(app);
        TestEntity entity2 = new TestEntityImpl(app);
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity2, TestEntity.NAME));
        Entities.manage(entity);
        Entities.manage(entity2);
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        entity2.setAttribute(TestEntity.NAME, "aval");
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval");
    }
    
    @Test
    public void testGetAttributeWhenReadyWithPostProcessingConfigReturnsWhenSet() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        TestEntity entity2 = new TestEntityImpl(app);
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributePostProcessedWhenReady(entity2, TestEntity.NAME, Predicates.notNull(), new Function<String,String>() {
            @Override public String apply(String input) {
                return (input == null) ? null : input+"mysuffix";
            }}));
        Entities.manage(entity);
        Entities.manage(entity2);
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        entity2.setAttribute(TestEntity.NAME, "aval");
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "avalmysuffix");
    }
    
    @Test
    public void testGetAttributeWhenReadyConfigBlocksUntilSet() throws Exception {
        TestEntity entity = new TestEntityImpl(app);
        final TestEntity entity2 = new TestEntityImpl(app);
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity2, TestEntity.NAME));
        Entities.manage(entity);
        Entities.manage(entity2);
        app.start(ImmutableList.of(new SimulatedLocation()));

        // previously was just sleep 10, and (endtime-starttime > 10); failed with exactly 10ms        
        final long sleepTime = 20;
        final long earlyReturnGrace = 5;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                Time.sleep(sleepTime);
                entity2.setAttribute(TestEntity.NAME, "aval");
            }});
        try {
            long starttime = System.currentTimeMillis();
            t.start();
            assertEquals(entity.getConfig(TestEntity.CONF_NAME), "aval");
            long endtime = System.currentTimeMillis();
            
            assertTrue((endtime - starttime) >= (sleepTime - earlyReturnGrace), "starttime=$starttime; endtime=$endtime");
            
        } finally {
            t.interrupt();
        }
    }

}
