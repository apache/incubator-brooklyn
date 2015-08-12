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

import java.util.concurrent.Callable;

import org.apache.brooklyn.management.ExecutionContext;
import org.apache.brooklyn.management.Task;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.util.guava.Maybe;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

/**
 * see also {@link TasksTest} for more tests
 */
@Test
public class ValueResolverTest extends BrooklynAppUnitTestSupport {

    private ExecutionContext executionContext;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        executionContext = app.getExecutionContext();
    }
    
    public static final Task<String> newSleepTask(final Duration timeout, final String result) {
        return Tasks.<String>builder().body(new Callable<String>() { 
            public String call() { 
                Time.sleep(timeout); 
                return result; 
            }}
        ).build();
    }
    
    public static final Task<String> newThrowTask(final Duration timeout) {
        return Tasks.<String>builder().body(new Callable<String>() { 
            public String call() {
                Time.sleep(timeout); 
                throw new IllegalStateException("intended, during tests");
            }}
        ).build();
    }
    
    public void testTimeoutZero() {
        Maybe<String> result = Tasks.resolving(newSleepTask(Duration.TEN_SECONDS, "foo")).as(String.class).context(executionContext).timeout(Duration.ZERO).getMaybe();
        Assert.assertFalse(result.isPresent());
    }
    
    public void testTimeoutBig() {
        Maybe<String> result = Tasks.resolving(newSleepTask(Duration.ZERO, "foo")).as(String.class).context(executionContext).timeout(Duration.TEN_SECONDS).getMaybe();
        Assert.assertEquals(result.get(), "foo");
    }

    public void testNoExecutionContextOnCompleted() {
        Task<String> t = newSleepTask(Duration.ZERO, "foo");
        executionContext.submit(t).getUnchecked();
        Maybe<String> result = Tasks.resolving(t).as(String.class).timeout(Duration.ZERO).getMaybe();
        Assert.assertEquals(result.get(), "foo");
    }

    public static Throwable assertThrowsOnMaybe(ValueResolver<?> result) {
        try {
            result = result.clone();
            result.getMaybe();
            Assert.fail("should have thrown");
            return null;
        } catch (Exception e) { return e; }
    }
    public static Throwable assertThrowsOnGet(ValueResolver<?> result) {
        result = result.clone();
        try {
            result.get();
            Assert.fail("should have thrown");
            return null;
        } catch (Exception e) { return e; }
    }
    public static <T> Maybe<T> assertMaybeIsAbsent(ValueResolver<T> result) {
        result = result.clone();
        Maybe<T> maybe = result.getMaybe();
        Assert.assertFalse(maybe.isPresent());
        return maybe;
    }
    
    public void testSwallowError() {
        ValueResolver<String> result = Tasks.resolving(newThrowTask(Duration.ZERO)).as(String.class).context(executionContext).swallowExceptions();
        assertMaybeIsAbsent(result);
        assertThrowsOnGet(result);
    }


    public void testDontSwallowError() {
        ValueResolver<String> result = Tasks.resolving(newThrowTask(Duration.ZERO)).as(String.class).context(executionContext);
        assertThrowsOnMaybe(result);
        assertThrowsOnGet(result);
    }

    public void testDefaultWhenSwallowError() {
        ValueResolver<String> result = Tasks.resolving(newThrowTask(Duration.ZERO)).as(String.class).context(executionContext).swallowExceptions().defaultValue("foo");
        assertMaybeIsAbsent(result);
        Assert.assertEquals(result.get(), "foo");
    }

    public void testDefaultBeforeDelayAndError() {
        ValueResolver<String> result = Tasks.resolving(newThrowTask(Duration.TEN_SECONDS)).as(String.class).context(executionContext).timeout(Duration.ZERO).defaultValue("foo");
        assertMaybeIsAbsent(result);
        Assert.assertEquals(result.get(), "foo");
    }

}
