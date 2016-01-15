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
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.BasicTask;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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

    private static final Logger log = LoggerFactory.getLogger(DependentConfigurationTest.class);
    
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
    public void testRegexReplacementFunctionWithStrings() throws Exception {
        Task<Function<String, String>> task = DependentConfiguration.regexReplacement("foo", "bar");
        submit(task);
        Function<String, String> regexReplacer = task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(regexReplacer.apply("somefootext"), "somebartext");
    }

    @Test
    public void testRegexReplacementFunctionWithAttributeWhenReady() throws Exception {
        AttributeSensor<Object> replacementSensor = Sensors.newSensor(Object.class, "test.replacement");
        Task<String> pattern = DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME);
        Task<Object> replacement = DependentConfiguration.attributeWhenReady(entity, replacementSensor);
        Task<Function<String, String>> task = DependentConfiguration.regexReplacement(pattern, replacement);
        submit(task);
        entity.sensors().set(TestEntity.NAME, "foo");
        entity.sensors().set(replacementSensor, "bar");
        Function<String, String> regexReplacer = task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(regexReplacer.apply("somefootext"), "somebartext");
    }

    @Test
    public void testRegexReplacementWithStrings() throws Exception {
        Task<String> task = DependentConfiguration.regexReplacement("somefootext", "foo", "bar");
        submit(task);
        String result = task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(result, "somebartext");
    }

    @Test
    public void testRegexReplacementWithAttributeWhenReady() throws Exception {
        AttributeSensor<String> sourceSensor = Sensors.newStringSensor("test.source");
        AttributeSensor<String> replacementSensor = Sensors.newSensor(String.class, "test.replacement");
        Task<String> source = DependentConfiguration.attributeWhenReady(entity, sourceSensor);
        Task<String> pattern = DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME);
        Task<String> replacement = DependentConfiguration.attributeWhenReady(entity, replacementSensor);
        Task<String> task = DependentConfiguration.regexReplacement(source, pattern, replacement);
        submit(task);
        entity.sensors().set(sourceSensor, "somefootext");
        entity.sensors().set(TestEntity.NAME, "foo");
        entity.sensors().set(replacementSensor, "bar");
        String result = task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(result, "somebartext");
    }

    @Test
    public void testAttributeWhenReady() throws Exception {
        final Task<String> t = submit(DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME));
        assertNotDoneContinually(t);
        
        entity.sensors().set(TestEntity.NAME, "myval");
        assertEquals(assertDoneEventually(t), "myval");
    }

    @Test
    public void testAttributeWhenReadyWithPredicate() throws Exception {
        final Task<String> t = submit(DependentConfiguration.attributeWhenReady(entity, TestEntity.NAME, Predicates.equalTo("myval2")));
        
        entity.sensors().set(TestEntity.NAME, "myval");
        assertNotDoneContinually(t);
        
        entity.sensors().set(TestEntity.NAME, "myval2");
        assertEquals(assertDoneEventually(t), "myval2");
    }

    @Test
    public void testAttributeWhenReadyWithPostProcessing() throws Exception {
        final Task<String> t = submit(DependentConfiguration.valueWhenAttributeReady(entity, TestEntity.SEQUENCE, Functions.toStringFunction()));
        assertNotDoneContinually(t);
        
        entity.sensors().set(TestEntity.SEQUENCE, 1);
        assertEquals(assertDoneEventually(t), "1");
    }

    @Test
    public void testAttributeWhenReadyWithPostProcessingWithBuilder() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.SEQUENCE)
                .postProcess(Functions.toStringFunction())
                .build());

        assertNotDoneContinually(t);
        
        entity.sensors().set(TestEntity.SEQUENCE, 1);
        assertEquals(assertDoneEventually(t), "1");
    }

    @Test
    public void testAttributeWhenReadyWithPostProcessingWithBuilderWaitingNow() throws Exception {
        final Task<String> t = submit(new Callable<String>() {
            public String call() {
                return DependentConfiguration.builder()
                        .attributeWhenReady(entity, TestEntity.SEQUENCE)
                        .postProcess(Functions.toStringFunction())
                        .runNow();
            }});

        assertNotDoneContinually(t);
        
        entity.sensors().set(TestEntity.SEQUENCE, 1);
        assertEquals(assertDoneEventually(t), "1");
    }

    @Test
    public void testAttributeWhenReadyWithAbortHappyPath() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .abortIf(entity2, TestEntity.SEQUENCE, Predicates.equalTo(1))
                .build());
        assertNotDoneContinually(t);
        
        entity.sensors().set(TestEntity.NAME, "myval");
        assertEquals(assertDoneEventually(t), "myval");
    }

    @Test
    public void testAttributeWhenReadyWithAbort() throws Exception {
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .abortIf(entity2, TestEntity.SEQUENCE, Predicates.equalTo(1))
                .build());

        assertNotDoneContinually(t);

        entity2.sensors().set(TestEntity.SEQUENCE, 321);
        assertNotDoneContinually(t);

        entity2.sensors().set(TestEntity.SEQUENCE, 1);
        try {
            assertDoneEventually(t);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Aborted waiting for ready")) throw e;
        }
    }

    @Test
    public void testAttributeWhenReadyWithAbortWaitingNow() throws Exception {
        final Task<String> t = submit(new Callable<String>() {
            public String call() {
                return DependentConfiguration.builder()
                        .attributeWhenReady(entity, TestEntity.NAME)
                        .abortIf(entity2, TestEntity.SEQUENCE, Predicates.equalTo(1))
                        .runNow();
            }});

        assertNotDoneContinually(t);

        entity2.sensors().set(TestEntity.SEQUENCE, 321);
        assertNotDoneContinually(t);

        entity2.sensors().set(TestEntity.SEQUENCE, 1);
        try {
            assertDoneEventually(t);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Aborted waiting for ready")) throw e;
        }
    }

    @Test
    public void testAttributeWhenReadyWithAbortFailsWhenAbortConditionAlreadyHolds() throws Exception {
        entity2.sensors().set(TestEntity.SEQUENCE, 1);
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
    public void testAttributeWhenReadyWithAbortFailsWhenAbortConditionAlreadyHoldsWaitingNow() throws Exception {
        entity2.sensors().set(TestEntity.SEQUENCE, 1);
        final Task<String> t = submit(new Callable<String>() {
            public String call() {
                return DependentConfiguration.builder()
                        .attributeWhenReady(entity, TestEntity.NAME)
                        .abortIf(entity2, TestEntity.SEQUENCE, Predicates.equalTo(1))
                        .runNow();
            }});
        try {
            assertDoneEventually(t);
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Aborted waiting for ready")) throw e;
        }
    }

    @Test
    public void testAttributeWhenReadyRunNowWithoutPostProcess() throws Exception {
        Task<String> t  = submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return DependentConfiguration.builder()
                        .attributeWhenReady(entity, TestEntity.NAME)
                        .runNow();
            }
        });
        entity.sensors().set(TestEntity.NAME, "myentity");
        assertDoneEventually(t);
        assertEquals(t.get(), "myentity");
    }

    @Test
    public void testAttributeWhenReadyAbortsWhenOnFireByDefault() {
        log.info("starting test "+JavaClassNames.niceClassAndMethod());
        final Task<String> t = submit(DependentConfiguration.builder()
                .attributeWhenReady(entity, TestEntity.NAME)
                .build());

        ServiceStateLogic.setExpectedState(entity, Lifecycle.ON_FIRE);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        try {
            assertDoneEventually(t);
            fail("Should have failed already!");
        } catch (Throwable e) {
            if (e.toString().contains("Aborted waiting for ready")) 
                return;
            
            log.warn("Did not abort as expected: "+e, e);
            Entities.dumpInfo(entity);
            
            throw Exceptions.propagate(e);
        }
    }

    @Test(invocationCount=100, groups = "Integration")
    public void testAttributeWhenReadyAbortsWhenOnfireByDefaultManyTimes() {
        testAttributeWhenReadyAbortsWhenOnFireByDefault();
    }
    
    @Test
    public void testAttributeWhenReadyAbortsWhenAlreadyOnFireByDefault() throws Exception {
        ServiceStateLogic.setExpectedState(entity, Lifecycle.ON_FIRE);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
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
        
        entity.sensors().set(TestEntity.NAME, "myval");
        assertNotDoneContinually(t);
        
        entity2.sensors().set(TestEntity.NAME, "myval2");
        assertEquals(ImmutableSet.copyOf(assertDoneEventually(t)), ImmutableSet.of("myval", "myval2"));
    }

    @Test
    public void testListAttributeWhenReadyFromMultipleEntitiesWithLocalReadinessPredicate() throws Exception {
        final Task<List<String>> t = submit(DependentConfiguration.builder()
                .attributeWhenReadyFromMultiple(ImmutableList.of(entity, entity2), TestEntity.NAME, StringPredicates.startsWith("myval"))
                .build());
        
        entity.sensors().set(TestEntity.NAME, "wrongval");
        entity2.sensors().set(TestEntity.NAME, "wrongval2");
        assertNotDoneContinually(t);
        
        entity.sensors().set(TestEntity.NAME, "myval");
        assertNotDoneContinually(t);
        entity2.sensors().set(TestEntity.NAME, "myval2");
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
        
        entity.sensors().set(TestEntity.SEQUENCE, 1);
        entity2.sensors().set(TestEntity.SEQUENCE, 2);
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
        T result = Asserts.succeedsEventually(MutableMap.of("timeout", Duration.FIVE_SECONDS), new Callable<T>() {
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
    
    private <T> Task<T> submit(Callable<T> job) {
        return app.getExecutionContext().submit(new BasicTask<T>(job));
    }
}
