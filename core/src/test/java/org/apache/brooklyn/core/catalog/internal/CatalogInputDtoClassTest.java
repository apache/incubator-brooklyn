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
package org.apache.brooklyn.core.catalog.internal;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogInput;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.reflect.TypeToken;

public class CatalogInputDtoClassTest {
    public interface CatalogInputTestEntity extends Entity {
        @CatalogConfig(label="String Key", priority=3)
        ConfigKey<String> STRING_KEY = ConfigKeys.newStringConfigKey("string_key");

        @CatalogConfig(label="Integer Key", priority=2)
        ConfigKey<Integer> INTEGER_KEY = ConfigKeys.newIntegerConfigKey("integer_key");

        @SuppressWarnings("serial")
        @CatalogConfig(label="Predicate Key", priority=1)
        ConfigKey<Predicate<String>> PREDICATE_KEY = ConfigKeys.newConfigKey(new TypeToken<Predicate<String>>() {}, "predicate_key");

        ConfigKey<String> UNPINNNED_KEY = ConfigKeys.newStringConfigKey("unpinned_key");
    }

    @Test
    public void testFullDefinition() {
        List<CatalogInput<?>> inputs = CatalogInputDto.ParseClassInputs.parseInputs(CatalogInputTestEntity.class);
        assertInput(inputs.get(0), "Predicate Key", true, CatalogInputTestEntity.PREDICATE_KEY);
        assertInput(inputs.get(1), "Integer Key", true, CatalogInputTestEntity.INTEGER_KEY);
        assertInput(inputs.get(2), "String Key", true, CatalogInputTestEntity.STRING_KEY);
        assertInput(inputs.get(3), "unpinned_key", false, CatalogInputTestEntity.UNPINNNED_KEY);
    }

    private void assertInput(CatalogInput<?> input, String label, boolean pinned, ConfigKey<?> type) {
        assertEquals(input.getLabel(), label);
        assertEquals(input.isPinned(), pinned);
        assertEquals(input.getType(), type);
    }

}
