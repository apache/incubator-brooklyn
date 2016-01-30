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
package org.apache.brooklyn.core.objs;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.reflect.TypeToken;

public class BasicSpecParameterFromClassTest {
    private ManagementContext mgmt;
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        mgmt = LocalManagementContextForTests.newInstance();
    }

    public interface SpecParameterTestEntity extends Entity {
        @CatalogConfig(label="String Key", priority=3)
        ConfigKey<String> STRING_KEY = ConfigKeys.newStringConfigKey("string_key");

        @CatalogConfig(label="Integer Key", priority=2)
        ConfigKey<Integer> INTEGER_KEY = ConfigKeys.newIntegerConfigKey("integer_key");

        @SuppressWarnings("serial")
        @CatalogConfig(label="Predicate Key", priority=1)
        ConfigKey<Predicate<String>> PREDICATE_KEY = ConfigKeys.newConfigKey(new TypeToken<Predicate<String>>() {}, "predicate_key");

        @SuppressWarnings("serial")
        @CatalogConfig(label="Hidden 1 Key", priority=-1)
        ConfigKey<Predicate<String>> HIDDEN1_KEY = ConfigKeys.newConfigKey(new TypeToken<Predicate<String>>() {}, "hidden1_key");

        @SuppressWarnings("serial")
        @CatalogConfig(label="Hidden 2 Key", priority=-2)
        ConfigKey<Predicate<String>> HIDDEN2_KEY = ConfigKeys.newConfigKey(new TypeToken<Predicate<String>>() {}, "hidden2_key");

        ConfigKey<String> UNPINNNED2_KEY = ConfigKeys.newStringConfigKey("unpinned2_key");
        ConfigKey<String> UNPINNNED1_KEY = ConfigKeys.newStringConfigKey("unpinned1_key");
    }

    @ImplementedBy(ConfigInImplParameterTestEntityImpl.class)
    public static interface ConfigInImplParameterTestEntity extends Entity {}
    public static class ConfigInImplParameterTestEntityImpl extends AbstractEntity implements ConfigInImplParameterTestEntity {
        public static final ConfigKey<String> SUGGESTED_VERSION = BrooklynConfigKeys.SUGGESTED_VERSION;
    }

    @Test
    public void testFullDefinition() {
        List<SpecParameter<?>> inputs = BasicSpecParameter.fromClass(mgmt, SpecParameterTestEntity.class);
        assertEquals(inputs.size(), 7);
        assertInput(inputs.get(0), "String Key", true, SpecParameterTestEntity.STRING_KEY);
        assertInput(inputs.get(1), "Integer Key", true, SpecParameterTestEntity.INTEGER_KEY);
        assertInput(inputs.get(2), "Predicate Key", true, SpecParameterTestEntity.PREDICATE_KEY);
        assertInput(inputs.get(3), "Hidden 1 Key", true, SpecParameterTestEntity.HIDDEN1_KEY);
        assertInput(inputs.get(4), "Hidden 2 Key", true, SpecParameterTestEntity.HIDDEN2_KEY);
        assertInput(inputs.get(5), "unpinned1_key", false, SpecParameterTestEntity.UNPINNNED1_KEY);
        assertInput(inputs.get(6), "unpinned2_key", false, SpecParameterTestEntity.UNPINNNED2_KEY);
    }
    
    @Test
    public void testDebug() throws ClassNotFoundException {
        System.out.println(BasicSpecParameter.fromClass(mgmt,  Class.forName("org.apache.brooklyn.entity.stock.BasicApplication")));
    }

    @Test
    public void testConfigInImplVisible() {
        List<SpecParameter<?>> inputs = BasicSpecParameter.fromClass(mgmt, ConfigInImplParameterTestEntity.class);
        assertEquals(inputs.size(), 1);
        ConfigKey<String> key = ConfigInImplParameterTestEntityImpl.SUGGESTED_VERSION;
        assertInput(inputs.get(0), key.getName(), false, key);
    }

    private void assertInput(SpecParameter<?> input, String label, boolean pinned, ConfigKey<?> type) {
        assertEquals(input.getLabel(), label);
        assertEquals(input.isPinned(), pinned);
        assertEquals(input.getConfigKey(), type);
    }

}
