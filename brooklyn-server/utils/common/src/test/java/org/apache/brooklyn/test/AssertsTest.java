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
package org.apache.brooklyn.test;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.Asserts.ShouldHaveFailedPreviouslyAssertionError;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.util.concurrent.Callables;

public class AssertsTest {

    private static final Runnable NOOP_RUNNABLE = new Runnable() {
        @Override public void run() {
        }
    };
    
    // TODO this is confusing -- i'd expect it to fail since it always returns false;
    // see notes at start of Asserts and in succeedsEventually method
    @Test
    public void testSucceedsEventually() {
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.millis(50)), Callables.returning(false));
    }
    
    @Test
    public void testAssertReturnsEventually() throws Exception {
        Asserts.assertReturnsEventually(NOOP_RUNNABLE, Duration.THIRTY_SECONDS);
    }
    
    @Test
    public void testAssertReturnsEventuallyTimesOut() throws Exception {
        final AtomicBoolean interrupted = new AtomicBoolean();
        
        try {
            Asserts.assertReturnsEventually(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(60*1000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }},
                Duration.of(10, TimeUnit.MILLISECONDS));
            Assert.fail("Should have thrown AssertionError on timeout");
        } catch (TimeoutException e) {
            // success
        }
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Assert.assertTrue(interrupted.get());
            }});
    }
    
    @Test
    public void testAssertReturnsEventuallyPropagatesException() throws Exception {
        try {
            Asserts.assertReturnsEventually(new Runnable() {
                public void run() {
                    throw new IllegalStateException("Simulating failure");
                }},
                Duration.THIRTY_SECONDS);
            Assert.fail("Should have thrown AssertionError on timeout");
        } catch (ExecutionException e) {
            IllegalStateException ise = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (ise == null || !ise.toString().contains("Simulating failure")) throw e;
        }
    }
    
    @Test
    public void testAssertStrings() {
        Asserts.assertStringContains("hello", "hello", "he");
        try {
            Asserts.assertStringContains("hello", "goodbye");
            Asserts.shouldHaveFailedPreviously();
        } catch (AssertionError e) { 
            Asserts.expectedFailureContains(e, "hello", "goodbye"); 
            Asserts.expectedFailureContainsIgnoreCase(e, "hello", "Goodbye"); 
        }
        
        Asserts.assertStringContainsIgnoreCase("hello", "Hello");
        try {
            Asserts.assertStringContains("hello", "Hello");
            Asserts.shouldHaveFailedPreviously();
        } catch (AssertionError e) { Asserts.expectedFailureContains(e, "hello", "Hello"); }
        
        Asserts.assertStringContainsAtLeastOne("hello", "hello", "goodbye");
        try {
            Asserts.assertStringContainsAtLeastOne("hello", "Hello", "goodbye");
            Asserts.shouldHaveFailedPreviously();
        } catch (AssertionError e) { Asserts.expectedFailureContains(e, "hello", "Hello", "goodbye"); }
        
        Asserts.assertStringMatchesRegex("hello", "hello", "he.*", "he[ckl]+e*o");
        try {
            Asserts.assertStringMatchesRegex("hello", "hello", "he");
            Asserts.shouldHaveFailedPreviously();
        } catch (AssertionError e) { Asserts.expectedFailureContains(e, "hello", "matchesRegex(he)"); }
    }

    @Test
    public void testExpectedFailures() {
        Asserts.expectedFailureOfType(new IllegalStateException(), 
            NoSuchElementException.class, IllegalStateException.class);
        Asserts.expectedFailureOfType(new IllegalStateException(), 
            NoSuchElementException.class, Object.class);
        
        try {
            Asserts.expectedFailureOfType(new IllegalStateException(), 
                NoSuchElementException.class, Error.class);
            Asserts.shouldHaveFailedPreviously();
        } catch (Throwable e) { Asserts.expectedFailure(e); }
    }
    
    @Test
    public void testShouldHaveFailed() {
        int reached = 0;
        try {
            try {
                // in normal tests the following indicates that an expected failure didn't happen
                // (but here we are testing it)
                Asserts.shouldHaveFailedPreviously();
                reached--;
            } catch (Throwable e) { 
                // in normal tests the following indicates that a failure was NOT thrown by the above
                // (but here the call *below* should rethrow)
                reached++;
                Asserts.expectedFailure(e); 
                reached--;
            }
            reached--;
            throw new AssertionError("It should have failed previously, with a "
                + "ShouldHaveFailedPreviouslyAssertionError in the first block rethrown by the second");
        } catch (ShouldHaveFailedPreviouslyAssertionError e) {
            // expected
            reached++;
        }
        reached++;
        // check code flowed the way we expected
        Asserts.assertEquals(reached, 3);
    }
}
