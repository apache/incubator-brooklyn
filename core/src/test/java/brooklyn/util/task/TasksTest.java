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
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.brooklyn.management.ExecutionContext;
import org.apache.brooklyn.management.Task;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.guava.Functionals;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Callables;


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
    
    @SuppressWarnings("unchecked")
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
    
    @Test
    public void testErrorsResolvingPropagatesOrSwallowedAllCorrectly() throws Exception {
        app.setConfig(TestEntity.CONF_OBJECT, ValueResolverTest.newThrowTask(Duration.ZERO));
        Task<Object> t = Tasks.builder().body(Functionals.callable(EntityFunctions.config(TestEntity.CONF_OBJECT), app)).build();
        ValueResolver<Object> v = Tasks.resolving(t).as(Object.class).context(app.getExecutionContext());
        
        ValueResolverTest.assertThrowsOnMaybe(v);
        ValueResolverTest.assertThrowsOnGet(v);
        
        v.swallowExceptions();
        ValueResolverTest.assertMaybeIsAbsent(v);
        ValueResolverTest.assertThrowsOnGet(v);
        
        v.defaultValue("foo");
        ValueResolverTest.assertMaybeIsAbsent(v);
        assertEquals(v.clone().get(), "foo");
        assertResolvesValue(v, Object.class, "foo");
    }

    @Test
    public void testRepeater() throws Exception {
        Task<?> t;
        
        t = Tasks.requiring(Repeater.create().until(Callables.returning(true)).every(Duration.millis(1))).build();
        app.getExecutionContext().submit(t);
        t.get(Duration.TEN_SECONDS);
        
        t = Tasks.testing(Repeater.create().until(Callables.returning(true)).every(Duration.millis(1))).build();
        app.getExecutionContext().submit(t);
        Assert.assertEquals(t.get(Duration.TEN_SECONDS), true);
        
        t = Tasks.requiring(Repeater.create().until(Callables.returning(false)).limitIterationsTo(2).every(Duration.millis(1))).build();
        app.getExecutionContext().submit(t);
        try {
            t.get(Duration.TEN_SECONDS);
            Assert.fail("Should have failed");
        } catch (Exception e) {
            // expected
        }

        t = Tasks.testing(Repeater.create().until(Callables.returning(false)).limitIterationsTo(2).every(Duration.millis(1))).build();
        app.getExecutionContext().submit(t);
        Assert.assertEquals(t.get(Duration.TEN_SECONDS), false);
    }

    @Test
    public void testRepeaterDescription() throws Exception{
        final String description = "task description";
        Repeater repeater = Repeater.create(description)
            .repeat(Callables.returning(null))
            .every(Duration.ONE_MILLISECOND)
            .limitIterationsTo(1)
            .until(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    TaskInternal<?> current = (TaskInternal<?>)Tasks.current();
                    assertEquals(current.getBlockingDetails(), description);
                    return true;
                }
            });
        Task<Boolean> t = Tasks.testing(repeater).build();
        app.getExecutionContext().submit(t);
        assertTrue(t.get(Duration.TEN_SECONDS));
    }

}
