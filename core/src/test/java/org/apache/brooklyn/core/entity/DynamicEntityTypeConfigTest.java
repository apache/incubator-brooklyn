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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.objs.BasicSpecParameter;
import org.apache.brooklyn.core.sensor.BasicSensorEvent;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.util.collections.MutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class DynamicEntityTypeConfigTest extends BrooklynAppUnitTestSupport {

    @ImplementedBy(ConfigEntityForTestingImpl.class)
    public static interface ConfigEntityForTesting extends Entity {
        ConfigKey<String> SUGGESTED_VERSION = BrooklynConfigKeys.SUGGESTED_VERSION;
        ConfigKey<String> INSTALL_UNIQUE_LABEL = BrooklynConfigKeys.INSTALL_UNIQUE_LABEL;
    }
    public static class ConfigEntityForTestingImpl extends AbstractEntity implements ConfigEntityForTesting {
        public static final ConfigKey<String> PRE_INSTALL_COMMAND = BrooklynConfigKeys.PRE_INSTALL_COMMAND;
        public static final ConfigKey<String> POST_INSTALL_COMMAND = BrooklynConfigKeys.POST_INSTALL_COMMAND;
    }
    private static final ConfigKey<?> NEW_CONFIG = ConfigKeys.newStringConfigKey("new.config");
    private static final ConfigKey<?> SPEC_CONFIG = ConfigKeys.newStringConfigKey("spec.config");
    
    private EntityInternal entity;
    @SuppressWarnings("rawtypes")
    private RecordingSensorEventListener<ConfigKey> listener;

    public final static Set<ConfigKey<?>> DEFAULT_CONFIG_KEYS = ImmutableSet.<ConfigKey<?>>of(
            ConfigEntityForTesting.SUGGESTED_VERSION,
            ConfigEntityForTesting.INSTALL_UNIQUE_LABEL,
            ConfigEntityForTestingImpl.PRE_INSTALL_COMMAND,
            ConfigEntityForTestingImpl.POST_INSTALL_COMMAND,
            SPEC_CONFIG); 

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception{
        super.setUp();
        entity = (EntityInternal) app.createAndManageChild(EntitySpec.create(ConfigEntityForTesting.class)
                .parameters(ImmutableList.of(new BasicSpecParameter<>("spec config", true, SPEC_CONFIG))));
        listener = new RecordingSensorEventListener<>();
        app.subscriptions().subscribe(entity, AbstractEntity.CONFIG_KEY_ADDED, listener);
        app.subscriptions().subscribe(entity, AbstractEntity.CONFIG_KEY_REMOVED, listener);
    }

    private void assertEventuallyListenerEventsEqual(@SuppressWarnings("rawtypes") final List<SensorEvent<ConfigKey>> sensorEvents) {
        EntityTypeTest.assertEventuallyListenerEventsEqual(listener, sensorEvents);
    }

    @Test
    public void testGetConfigKeys() throws Exception{
        assertEquals(entity.getEntityType().getConfigKeys(), DEFAULT_CONFIG_KEYS);
    }

    @Test
    public void testAddConfigKey() throws Exception{
        entity.getMutableEntityType().addConfigKey(NEW_CONFIG);
        assertEquals(entity.getEntityType().getConfigKeys(), 
                ImmutableSet.builder().addAll(DEFAULT_CONFIG_KEYS).add(NEW_CONFIG).build());
        
        assertEventuallyListenerEventsEqual(ImmutableList.of(BasicSensorEvent.ofUnchecked(AbstractEntity.CONFIG_KEY_ADDED, entity, NEW_CONFIG)));
    }

    @Test
    public void testAddConfigKeyThroughEntity() throws Exception{
        ((AbstractEntity)Entities.deproxy(entity)).configure(ImmutableList.<ConfigKey<?>>of(NEW_CONFIG));
        assertEquals(entity.getEntityType().getConfigKeys(), 
                ImmutableSet.builder().addAll(DEFAULT_CONFIG_KEYS).add(NEW_CONFIG).build());
        
        assertEventuallyListenerEventsEqual(ImmutableList.of(BasicSensorEvent.ofUnchecked(AbstractEntity.CONFIG_KEY_ADDED, entity, NEW_CONFIG)));
    }

    @Test
    public void testRemoveConfigKey() throws Exception {
        entity.getMutableEntityType().removeConfigKey(ConfigEntityForTesting.INSTALL_UNIQUE_LABEL);
        assertEquals(entity.getEntityType().getConfigKeys(), 
                MutableSet.builder().addAll(DEFAULT_CONFIG_KEYS).remove(ConfigEntityForTesting.INSTALL_UNIQUE_LABEL).build().asUnmodifiable());
        
        assertEventuallyListenerEventsEqual(ImmutableList.of(
            BasicSensorEvent.ofUnchecked(AbstractEntity.CONFIG_KEY_REMOVED, entity, ConfigEntityForTesting.INSTALL_UNIQUE_LABEL)));
    }

    @Test
    public void testGetConfigKey() throws Exception {
        ConfigKey<?> expected = ConfigEntityForTesting.INSTALL_UNIQUE_LABEL;
        ConfigKey<?> configKey = entity.getEntityType().getConfigKey(expected.getName());
        assertEquals(configKey.getDescription(), expected.getDescription());
        assertEquals(configKey.getName(), expected.getName());
        
        assertNull(entity.getEntityType().getConfigKey("does.not.exist"));
    }

}
