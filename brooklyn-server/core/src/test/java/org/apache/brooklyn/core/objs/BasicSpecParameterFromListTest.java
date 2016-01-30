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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.core.objs.BasicSpecParameter;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.text.StringPredicates;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class BasicSpecParameterFromListTest {
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        mgmt = LocalManagementContextForTests.newInstance();
    }

    @Test
    public void testInlineName() {
        String name = "minRam";
        SpecParameter<?> input = parse(name);
        assertEquals(input.getLabel(), name);
        assertTrue(input.isPinned());
        ConfigKey<?> type = input.getConfigKey();
        assertEquals(type.getName(), name);
        assertEquals(type.getTypeToken(), TypeToken.of(String.class));
        assertNull(type.getDefaultValue());
        assertNull(type.getDescription());
        assertNull(type.getInheritance());
        assertConstraint(type.getConstraint(), Predicates.alwaysTrue());
    }

    @Test
    public void testOnlyName() {
        String name = "minRam";
        SpecParameter<?> input = parse(ImmutableMap.of("name", name));
        assertEquals(input.getLabel(), name);
        assertEquals(input.getConfigKey().getName(), name);
        assertEquals(input.getConfigKey().getTypeToken(), TypeToken.of(String.class));
    }

    @Test
    public void testUnusualName() {
        parse(ImmutableMap.of("name", "name with spaces"));
    }

    @Test
    public void testFullDefinition() {
        String name = "minRam";
        String label = "Minimum Ram";
        String description = "Some description";
        String inputType = "string";
        String defaultValue = "VALUE";
        String constraint = "required";
        SpecParameter<?> input = parse(ImmutableMap.builder()
                .put("name", name)
                .put("label", label)
                .put("description", description)
                .put("type", inputType)
                .put("default", defaultValue)
                .put("constraints", constraint)
                .build());

        assertEquals(input.getLabel(), label);
        assertTrue(input.isPinned());

        ConfigKey<?> type = input.getConfigKey();
        assertEquals(type.getName(), name);
        assertEquals(type.getTypeToken(), TypeToken.of(String.class));
        assertEquals(type.getDefaultValue(), defaultValue);
        assertEquals(type.getDescription(), description);
        assertNull(type.getInheritance());
        assertConstraint(type.getConstraint(), StringPredicates.isNonBlank());
    }

    @Test
    public void testUnexpectedType() {
        String name = "1234";
        String label = "1234";
        String description = "5678.56";
        String defaultValue = "444.12";
        SpecParameter<?> input = parse(ImmutableMap.of(
                "name", name,
                "label", label,
                "description", description,
                "default", defaultValue));

        assertEquals(input.getLabel(), name);
        assertTrue(input.isPinned());

        ConfigKey<?> type = input.getConfigKey();
        assertEquals(type.getName(), name);
        assertEquals(type.getDefaultValue(), defaultValue);
        assertEquals(type.getDescription(), description);
        assertNull(type.getInheritance());
    }

    @Test
    public void testConstraintAsArray() {
        String name = "minRam";
        String constraint = "required";
        SpecParameter<?> input = parse(ImmutableMap.of(
                "name", name,
                "constraints", ImmutableList.of(constraint)));
        ConfigKey<?> type = input.getConfigKey();
        assertConstraint(type.getConstraint(), StringPredicates.isNonBlank());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMissingName() {
        parse(ImmutableMap.of(
                "type", "string"));
    }

    @Test
    public void testJavaType() {
        String name = "minRam";
        SpecParameter<?> input = parse(ImmutableMap.of(
                "name", name,
                "type", BasicSpecParameterFromListTest.class.getName()));
        assertEquals(input.getConfigKey().getTypeToken(), TypeToken.of(BasicSpecParameterFromListTest.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidType() {
        String name = "minRam";
        parse(ImmutableMap.of(
                "name", name,
                "type", "missing_type"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidConstraint() {
        String name = "minRam";
        parse(ImmutableMap.of(
                "name", name,
                "type", "missing_type"));
    }

    private SpecParameter<?> parse(Object def) {
        BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
        List<SpecParameter<?>> inputs = BasicSpecParameter.fromConfigList(ImmutableList.of(def), loader);
        return Iterables.getOnlyElement(inputs);
    }

    private void assertConstraint(Predicate<?> actual, Predicate<?> expected) {
        //How to compare predicates correctly, re-creating the same predicate doesn't work
        assertEquals(actual.toString(), expected.toString());
    }

}
