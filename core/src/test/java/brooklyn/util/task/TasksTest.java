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
package brooklyn.util.task;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static org.testng.Assert.assertEquals;

import java.util.Map;
import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.management.ExecutionContext;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;


public class TasksTest extends BrooklynAppUnitTestSupport {

    private ExecutionContext executionContext;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        executionContext = app.getExecutionContext();
    }
    
    @Test
    public void testResolveNull() throws Exception {
        assertResolvesValue(null, String.class, null);
    }
    
    @Test
    public void testResolveValueCastsToType() throws Exception {
        assertResolvesValue(123, String.class, "123");
    }
    
    @Test
    public void testResolvesAttributeWhenReady() throws Exception {
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");
        assertResolvesValue(attributeWhenReady(app, TestApplication.MY_ATTRIBUTE), String.class, "myval");
    }
    
    @Test
    public void testResolvesMapWithAttributeWhenReady() throws Exception {
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");
        Map<?,?> orig = ImmutableMap.of("mykey", attributeWhenReady(app, TestApplication.MY_ATTRIBUTE));
        Map<?,?> expected = ImmutableMap.of("mykey", "myval");
        assertResolvesValue(orig, String.class, expected);
    }
    
    @Test
    public void testResolvesSetWithAttributeWhenReady() throws Exception {
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");
        Set<?> orig = ImmutableSet.of(attributeWhenReady(app, TestApplication.MY_ATTRIBUTE));
        Set<?> expected = ImmutableSet.of("myval");
        assertResolvesValue(orig, String.class, expected);
    }
    
    @Test
    public void testResolvesMapOfMapsWithAttributeWhenReady() throws Exception {
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");
        Map<?,?> orig = ImmutableMap.of("mykey", ImmutableMap.of("mysubkey", attributeWhenReady(app, TestApplication.MY_ATTRIBUTE)));
        Map<?,?> expected = ImmutableMap.of("mykey", ImmutableMap.of("mysubkey", "myval"));
        assertResolvesValue(orig, String.class, expected);
    }
    
    @Test
    public void testResolvesIterableOfMapsWithAttributeWhenReady() throws Exception {
        app.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");
        // using Iterables.concat so that orig is of type FluentIterable rather than List etc
        Iterable<?> orig = Iterables.concat(ImmutableList.of(ImmutableMap.of("mykey", attributeWhenReady(app, TestApplication.MY_ATTRIBUTE))));
        Iterable<Map<?,?>> expected = ImmutableList.<Map<?,?>>of(ImmutableMap.of("mykey", "myval"));
        assertResolvesValue(orig, String.class, expected);
    }
    
    private void assertResolvesValue(Object actual, Class<?> type, Object expected) throws Exception {
        Object result = Tasks.resolveValue(actual, type, executionContext);
        assertEquals(result, expected);
    }
}
