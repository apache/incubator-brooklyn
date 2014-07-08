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
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableList;
import brooklyn.util.task.BasicTask;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Callables;

/** Tests the standalone routines in dependent configuration.
 * See e.g. LocalEntitiesTest for tests of attributeWhenReady etc.
 */
public class DependentConfigurationTest extends BrooklynAppUnitTestSupport {

    public static final int SHORT_WAIT_MS = 100;
    public static final int TIMEOUT_MS = 30*1000;
    
    private TestEntity entity;
    private TestEntity entity2;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @Test
    public void testTransform() throws Exception {
        Task<Integer> t = DependentConfiguration.transform(
                new BasicTask<Integer>(Callables.returning(2)), 
                incrementerFunction());
        submit(t);
        assertEquals(t.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), Integer.valueOf(3));
    }

    private Function<Integer, Integer> incrementerFunction() {
        return new Function<Integer, Integer>() {
            @Override public Integer apply(Integer val) {
                return val + 1;
            }};
    }
    
    @Test
    public void testFormatString() throws Exception {
        Task<String> t = DependentConfiguration.formatString("%s://%s:%d/",
                "http",
                new BasicTask<String>(Callables.returning("localhost")), 
                DependentConfiguration.transform(new BasicTask<Integer>(Callables.returning(8080)), incrementerFunction()));
        submit(t);
        Assert.assertEquals(t.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), "http://localhost:8081/");
    }

    @Test
    public void testAttributeWhenReady() throws Exception {
        final Task<String> t = submit(DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME));
        assertNotDoneContinually(t);
        
        entity.setAttribute(TestEntity.NAME, "myval");
        assertEquals(assertDoneEventually(t), "myval");
    }

    @Test
    public void testAttributeWhenReadyWithPredicate() throws Exception {
        final Task<String> t = submit(DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME, Predicates.equalTo("myval2")));
        
        entity.setAttribute(TestEntity.NAME, "myval");
        assertNotDoneContinually(t);
        
        entity.setAttribute(TestEntity.NAME, "myval2");
        assertEquals(assertDoneEventually(t), "myval2");
    }

    @Test
    public void testAttributeWhenReadyWithPostProcessing() throws Exception {
        final Task<String> t = submit(DependentConfiguration.valueWhenAttributeReady(entity, TestEntity.SEQUENCE, Functions.toStringFunction()));
        assertNotDoneContinually(t);
        
        entity.setAttribute(TestEntity.SEQUENCE, 1);
        assertEquals(assertDoneEventually(t), "1");
    }

    @Test
    public void testAttributeWhenReadyWithPostProcessingWithBuilder() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.SEQUENCE)
                .postProcess(Functions.toStringFunction())
                .build());
        assertNotDoneContinually(t);
        
        entity.setAttribute(TestEntity.SEQUENCE, 1);
        assertEquals(assertDoneEventually(t), "1");
    }

    @Test
    public void testAttributeWhenReadyWithAbortHappyPath() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .abortIf(entity2, TestEntity.SEQUENCE, Predicates.equalTo(1))
                .build());
        assertNotDoneContinually(t);
        
        entity.setAttribute(TestEntity.NAME, "myval");
        assertEquals(assertDoneEventually(t), "myval");
    }

    @Test
    public void testAttributeWhenReadyWithAbort() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .abortIf(entity2, TestEntity.SEQUENCE, Predicates.equalTo(1))
                .build());
        assertNotDoneContinually(t);

        entity2.setAttribute(TestEntity.SEQUENCE, 321);
        assertNotDoneContinually(t);

        entity2.setAttribute(TestEntity.SEQUENCE, 1);
        try {
            assertDoneEventually(t);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Aborted waiting for ready")) throw e;
        }
    }

    @Test
    public void testAttributeWhenReadyWithAbortFailsWhenAbortConditionAlreadyHolds() throws Exception {
        entity2.setAttribute(TestEntity.SEQUENCE, 1);
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .abortIf(entity2, TestEntity.SEQUENCE, Predicates.equalTo(1))
                .build());
        try {
            assertDoneEventually(t);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Aborted waiting for ready")) throw e;
        }
    }

    @Test
    public void testAttributeWhenReadyAbortsWhenOnfireByDefault() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .build());

        entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
        try {
            assertDoneEventually(t);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Aborted waiting for ready")) throw e;
        }
    }

    @Test
    public void testAttributeWhenReadyAbortsWhenAlreadyOnfireByDefault() throws Exception {
        entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
        
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .build());

        try {
            assertDoneEventually(t);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Aborted waiting for ready")) throw e;
        }
    }

    @Test
    public void testListAttributeWhenReadyFromMultipleEntities() throws Exception {
        final Task<List<String>> t = submit(DependentConfiguration.builder()
                .attributeWhenReadyFromMultiple(ImmutableList.of(entity, entity2), TestEntity.NAME)
                .build());
        assertNotDoneContinually(t);
        
        entity.setAttribute(TestEntity.NAME, "myval");
        assertNotDoneContinually(t);
        
        entity2.setAttribute(TestEntity.NAME, "myval2");
        assertEquals(ImmutableSet.copyOf(assertDoneEventually(t)), ImmutableSet.of("myval", "myval2"));
    }

    @Test
    public void testListAttributeWhenReadyFromMultipleEntitiesWithLocalReadinessPredicate() throws Exception {
        final Task<List<String>> t = submit(DependentConfiguration.builder()
                .attributeWhenReadyFromMultiple(ImmutableList.of(entity, entity2), TestEntity.NAME, StringPredicates.startsWith("myval"))
                .build());
        
        entity.setAttribute(TestEntity.NAME, "wrongval");
        entity2.setAttribute(TestEntity.NAME, "wrongval2");
        assertNotDoneContinually(t);
        
        entity.setAttribute(TestEntity.NAME, "myval");
        assertNotDoneContinually(t);
        entity2.setAttribute(TestEntity.NAME, "myval2");
        assertEquals(ImmutableSet.copyOf(assertDoneEventually(t)), ImmutableSet.of("myval", "myval2"));
    }

    @Test
    public void testListAttributeWhenReadyFromMultipleEntitiesWithGlobalPostProcessor() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReadyFromMultiple(ImmutableList.of(entity, entity2), TestEntity.SEQUENCE)
                .postProcessFromMultiple(new Function<List<Integer>, String>() {
                        @Override public String apply(List<Integer> input) {
                            if (input == null) {
                                return null;
                            } else {
                                MutableList<Integer> inputCopy = MutableList.copyOf(input);
                                Collections.sort(inputCopy);
                                return Joiner.on(",").join(inputCopy);
                            }
                        }})
                .build());
        
        entity.setAttribute(TestEntity.SEQUENCE, 1);
        entity2.setAttribute(TestEntity.SEQUENCE, 2);
        assertEquals(assertDoneEventually(t), "1,2");
    }

    private void assertNotDoneContinually(final Task<?> t) {
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), new Callable<Void>() {
            @Override public Void call() throws Exception {
                if (t.isDone()) {
                    fail("task unexpectedly done: t="+t+"; result="+t.get());
                }
                return null;
            }
        });
    }
    
    private <T> T assertDoneEventually(final Task<T> t) throws Exception {
        final AtomicReference<ExecutionException> exception = new AtomicReference<ExecutionException>();
        T result = Asserts.succeedsEventually(new Callable<T>() {
            @Override public T call() throws InterruptedException, TimeoutException {
                try {
                    return t.get(Duration.ONE_SECOND);
                } catch (ExecutionException e) {
                    exception.set(e);
                    return null;
                } catch (InterruptedException e) {
                    throw e;
                } catch (TimeoutException e) {
                    throw e;
                }
            }
        });
        if (exception.get() != null) {
            throw exception.get();
        }
        return result;
    }

    
    private <T> Task<T> submit(Task<T> task) {
        return app.getExecutionContext().submit(task);
    }
}
