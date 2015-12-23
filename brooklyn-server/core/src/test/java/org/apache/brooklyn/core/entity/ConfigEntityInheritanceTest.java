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
package org.apache.brooklyn.core.entity;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.internal.ConfigMapTest.MyOtherEntity;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey.IntegerAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
        app = TestApplication.Factory.newManagedInstanceForTests();
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

    // --------------------

    @Test
    public void testConfigKeysInheritance() throws Exception {
        app.config().set(MyEntityWithPartiallyHeritableConfig.HERITABLE, "heritable");
        app.config().set(MyEntityWithPartiallyHeritableConfig.UNINHERITABLE, "uninheritable");
        app.config().set(MyEntityWithPartiallyHeritableConfig.ALWAYS_HERITABLE, "always_heritable");
        Entity child = app.addChild(EntitySpec.create(MyEntityWithPartiallyHeritableConfig.class));
        
        Assert.assertNotNull(child.getConfig(MyEntityWithPartiallyHeritableConfig.HERITABLE));
        Assert.assertNull(child.getConfig(MyEntityWithPartiallyHeritableConfig.UNINHERITABLE), null);
        Assert.assertNotNull(child.getConfig(MyEntityWithPartiallyHeritableConfig.ALWAYS_HERITABLE));
    }
    
    public static class MyEntityWithPartiallyHeritableConfig extends AbstractEntity {
        public static final ConfigKey<String> HERITABLE = ConfigKeys.builder(String.class, "herit.default").build();
        public static final ConfigKey<String> UNINHERITABLE = ConfigKeys.builder(String.class, "herit.none").inheritance(ConfigInheritance.NONE).build();
        // i find a strange joy in words where the prefix "in-" does not mean not, like inflammable 
        public static final ConfigKey<String> ALWAYS_HERITABLE = ConfigKeys.builder(String.class, "herit.always").inheritance(ConfigInheritance.ALWAYS).build();
    }

}
