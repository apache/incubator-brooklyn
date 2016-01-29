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

import groovy.lang.Closure;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.time.CountdownTimer;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * TODO should move this to new package brooklyn.util.assertions
 * and TODO should add a repeating() method which returns an AssertingRepeater extending Repeater
 * and:
 * <ul>
 * <li> adds support for requireAllIterationsTrue
 * <li> convenience run methods equivalent to succeedsEventually and succeedsContinually
 * </ul>
 * <p>
 * NOTE Selected routines in this class are originally copied from <a href="http://testng.org">TestNG</a>, to allow us to make assertions without having to
 * introduce a runtime dependency on TestNG.
 * </p>
 */
@Beta
public class Asserts {

    /** 
     * Timeout for use when something should happen within several seconds,
     * but there might be network calls or computation so {@link #DEFAULT_SHORT_TIMEOUT} is not applicable.
     */
    public static final Duration DEFAULT_LONG_TIMEOUT = Duration.THIRTY_SECONDS;
    
    /** 
     * Timeout for use when waiting for other threads to finish.
     * <p>
     * Long enough for parallel execution to catch up, 
     * even on overloaded mediocre test boxes most of the time,
     * but short enough not to irritate you when your test is failing. */
    public static final Duration DEFAULT_SHORT_TIMEOUT = Duration.ONE_SECOND;
    
    /** @deprecated since 0.9.0 use {@link #DEFAULT_LONG_TIMEOUT} */ @Deprecated
    public static final Duration DEFAULT_TIMEOUT = DEFAULT_LONG_TIMEOUT;
    
    private static final Duration DEFAULT_SHORT_PERIOD = Repeater.DEFAULT_REAL_QUICK_PERIOD;

    private static final Logger log = LoggerFactory.getLogger(Asserts.class);

    private Asserts() {}

    private static final Character OPENING_CHARACTER = '[';
    private static final Character CLOSING_CHARACTER = ']';

    private static final String ASSERT_LEFT = "expected " + OPENING_CHARACTER;
    private static final String ASSERT_MIDDLE = CLOSING_CHARACTER + " but found " + OPENING_CHARACTER;
    private static final String ASSERT_RIGHT = Character.toString(CLOSING_CHARACTER);

    static String format(Object actual, Object expected, String message) {
        String formatted = "";
        if (null != message) {
            formatted = message + " ";
        }

        return formatted + ASSERT_LEFT + expected + ASSERT_MIDDLE + actual + ASSERT_RIGHT;
    }

    static private void failNotEquals(Object actual , Object expected, String message ) {
        fail(format(actual, expected, message));
    }

    /**
     * Assert that an object reference is null.
     *
     * @param object The object reference.
     *
     * @throws AssertionError if the assertion fails.
     */
    static public void assertNull(final Object object) {
        assertNull(object, null);
    }

    /**
     * Assert that an object reference is not null.
     *
     * @param object The object reference.
     *
     * @throws AssertionError if the assertion fails.
     */
    static public void assertNotNull(final Object object) {
        assertNotNull(object, null);
    }

    /**
     * Assert that an object reference is null.
     *
     * @param object The object reference.
     * @param message The assertion error message.
     *
     * @throws AssertionError if the assertion fails.
     */
    static public void assertNull(final Object object, final String message) {
        if (null != object) {
            throw new AssertionError(message == null ? "object reference is not null" : message);
        }
    }

    /**
     * Assert that an object reference is not null.
     *
     * @param object The object reference.
     * @param message The assertion error message.
     *
     * @throws AssertionError if the assertion fails.
     */
    static public void assertNotNull(final Object object, final String message) {
        if (null == object) {
            throw new AssertionError(message == null ? "object reference is null" : message);
        }
    }

    /**
     * Asserts that two collections contain the same elements in the same order. If they do not,
     * an AssertionError is thrown.
     *
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(Collection<?> actual, Collection<?> expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two collections contain the same elements in the same order. If they do not,
     * an AssertionError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(Collection<?> actual, Collection<?> expected, String message) {
        if(actual == expected) {
            return;
        }

        if (actual == null || expected == null) {
            if (message != null) {
                fail(message);
            } else {
                fail("Collections not equal: expected: " + expected + " and actual: " + actual);
            }
        }

        assertEquals(actual.size(), expected.size(), message + ": lists don't have the same size");

        Iterator<?> actIt = actual.iterator();
        Iterator<?> expIt = expected.iterator();
        int i = -1;
        while(actIt.hasNext() && expIt.hasNext()) {
            i++;
            Object e = expIt.next();
            Object a = actIt.next();
            String explanation = "Lists differ at element [" + i + "]: " + e + " != " + a;
            String errorMessage = message == null ? explanation : message + ": " + explanation;

            assertEquals(a, e, errorMessage);
        }
    }

    /** Asserts that two iterators return the same elements in the same order. If they do not,
     * an AssertionError is thrown.
     * Please note that this assert iterates over the elements and modifies the state of the iterators.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(Iterator<?> actual, Iterator<?> expected) {
        assertEquals(actual, expected, null);
    }

    /** Asserts that two iterators return the same elements in the same order. If they do not,
     * an AssertionError, with the given message, is thrown.
     * Please note that this assert iterates over the elements and modifies the state of the iterators.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(Iterator<?> actual, Iterator<?> expected, String message) {
        if(actual == expected) {
            return;
        }

        if(actual == null || expected == null) {
            if(message != null) {
                fail(message);
            } else {
                fail("Iterators not equal: expected: " + expected + " and actual: " + actual);
            }
        }

        int i = -1;
        while(actual.hasNext() && expected.hasNext()) {

            i++;
            Object e = expected.next();
            Object a = actual.next();
            String explanation = "Iterators differ at element [" + i + "]: " + e + " != " + a;
            String errorMessage = message == null ? explanation : message + ": " + explanation;

            assertEquals(a, e, errorMessage);

        }

        if(actual.hasNext()) {

            String explanation = "Actual iterator returned more elements than the expected iterator.";
            String errorMessage = message == null ? explanation : message + ": " + explanation;
            fail(errorMessage);

        } else if(expected.hasNext()) {

            String explanation = "Expected iterator returned more elements than the actual iterator.";
            String errorMessage = message == null ? explanation : message + ": " + explanation;
            fail(errorMessage);

        }

    }

    /** Asserts that two iterables return iterators with the same elements in the same order. If they do not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(Iterable<?> actual, Iterable<?> expected) {
        assertEquals(actual, expected, null);
    }

    /** Asserts that two iterables return iterators with the same elements in the same order. If they do not,
     * an AssertionError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(Iterable<?> actual, Iterable<?> expected, String message) {
        if(actual == expected) {
            return;
        }

        if(actual == null || expected == null) {
            if(message != null) {
                fail(message);
            } else {
                fail("Iterables not equal: expected: " + expected + " and actual: " + actual);
            }
        }

        Iterator<?> actIt = actual.iterator();
        Iterator<?> expIt = expected.iterator();

        assertEquals(actIt, expIt, message);
    }



    /**
     * Asserts that two sets are equal.
     */
    static public void assertEquals(Set<?> actual, Set<?> expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Assert set equals
     */
    static public void assertEquals(Set<?> actual, Set<?> expected, String message) {
        if (actual == expected) {
            return;
        }

        if (actual == null || expected == null) {
            // Keep the back compatible
            if (message == null) {
                fail("Sets not equal: expected: " + expected + " and actual: " + actual);
            } else {
                failNotEquals(actual, expected, message);
            }
        }

        if (!actual.equals(expected)) {
            if (message == null) {
                fail("Sets differ: expected " + expected + " but got " + actual);
            } else {
                failNotEquals(actual, expected, message);
            }
        }
    }

    /**
     * Asserts that two maps are equal.
     */
    static public void assertEquals(Map<?, ?> actual, Map<?, ?> expected) {
        if (actual == expected) {
            return;
        }

        if (actual == null || expected == null) {
            fail("Maps not equal: expected: " + expected + " and actual: " + actual);
        }

        if (actual.size() != expected.size()) {
            fail("Maps do not have the same size:" + actual.size() + " != " + expected.size());
        }

        Set<?> entrySet = actual.entrySet();
        for (Iterator<?> iterator = entrySet.iterator(); iterator.hasNext();) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) iterator.next();
            Object key = entry.getKey();
            Object value = entry.getValue();
            Object expectedValue = expected.get(key);
            assertEquals(value, expectedValue, "Maps do not match for key:" + key + " actual:" + value
                    + " expected:" + expectedValue);
        }

    }


    /**
     * Asserts that two arrays contain the same elements in the same order. If they do not,
     * an AssertionError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(Object[] actual, Object[] expected, String message) {
        if(actual == expected) {
            return;
        }

        if ((actual == null && expected != null) || (actual != null && expected == null)) {
            if (message != null) {
                fail(message);
            } else {
                fail("Arrays not equal: " + Arrays.toString(expected) + " and " + Arrays.toString(actual));
            }
        }
        assertEquals(Arrays.asList(actual), Arrays.asList(expected), message);
    }

    /**
     * Asserts that two arrays contain the same elements in the same order. If they do not,
     * an AssertionError is thrown.
     *
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(Object[] actual, Object[] expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two objects are equal. 
     * @param actual the actual value
     * @param expected the expected value
     *                 
     * @throws AssertionError if the values are not equal.
     */
    static public void assertEquals(Object actual, Object expected) {
        assertEquals(actual, expected, null);
    }
    
    /**
     * Asserts that two objects are equal. 
     * 
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     *
     * @throws AssertionError if the values are not equal.
     */
    static public void assertEquals(Object actual, Object expected, String message) {
        if((expected == null) && (actual == null)) {
            return;
        }
        if(expected != null) {
            if (expected.getClass().isArray()) {
                assertArrayEquals(actual, expected, message);
                return;
            } else if (expected.equals(actual)) {
                return;
            }
        }
        failNotEquals(actual, expected, message);
    }


    /**
     * Asserts that two objects are equal. It they are not, an AssertionError,
     * with given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value (should be an non-null array value)
     * @param message the assertion error message
     */
    private static void assertArrayEquals(Object actual, Object expected, String message) {
        //is called only when expected is an array
        if (actual.getClass().isArray()) {
            int expectedLength = Array.getLength(expected);
            if (expectedLength == Array.getLength(actual)) {
                for (int i = 0 ; i < expectedLength ; i++) {
                    Object _actual = Array.get(actual, i);
                    Object _expected = Array.get(expected, i);
                    try {
                        assertEquals(_actual, _expected);
                    } catch (AssertionError ae) {
                        failNotEquals(actual, expected, message == null ? "" : message
                                + " (values at index " + i + " are not the same)");
                    }
                }
                //array values matched
                return;
            } else {
                failNotEquals(Array.getLength(actual), expectedLength, message == null ? "" : message
                        + " (Array lengths are not the same)");
            }
        }
        failNotEquals(actual, expected, message);
    }


    /**
     * Asserts that two Strings are equal. If they are not,
     * an AssertionError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(String actual, String expected, String message) {
        assertEquals((Object) actual, (Object) expected, message);
    }

    /**
     * Asserts that two Strings are equal. If they are not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(String actual, String expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two doubles are equal within a delta.  If they are not,
     * an AssertionError, with the given message, is thrown.  If the expected
     * value is infinity then the delta value is ignored.
     * @param actual the actual value
     * @param expected the expected value
     * @param delta the absolute tolerable difference between the actual and expected values
     * @param message the assertion error message
     */
    static public void assertEquals(double actual, double expected, double delta, String message) {
        // handle infinity specially since subtracting to infinite values gives NaN and the
        // the following test fails
        if(Double.isInfinite(expected)) {
            if(!(expected == actual)) {
                failNotEquals(new Double(actual), new Double(expected), message);
            }
        }
        else if(!(Math.abs(expected - actual) <= delta)) { // Because comparison with NaN always returns false
            failNotEquals(new Double(actual), new Double(expected), message);
        }
    }

    /**
     * Asserts that two doubles are equal within a delta. If they are not,
     * an AssertionError is thrown. If the expected value is infinity then the
     * delta value is ignored.
     * @param actual the actual value
     * @param expected the expected value
     * @param delta the absolute tolerable difference between the actual and expected values
     */
    static public void assertEquals(double actual, double expected, double delta) {
        assertEquals(actual, expected, delta, null);
    }

    /**
     * Asserts that two floats are equal within a delta. If they are not,
     * an AssertionError, with the given message, is thrown.  If the expected
     * value is infinity then the delta value is ignored.
     * @param actual the actual value
     * @param expected the expected value
     * @param delta the absolute tolerable difference between the actual and expected values
     * @param message the assertion error message
     */
    static public void assertEquals(float actual, float expected, float delta, String message) {
        // handle infinity specially since subtracting to infinite values gives NaN and the
        // the following test fails
        if(Float.isInfinite(expected)) {
            if(!(expected == actual)) {
                failNotEquals(new Float(actual), new Float(expected), message);
            }
        }
        else if(!(Math.abs(expected - actual) <= delta)) {
            failNotEquals(new Float(actual), new Float(expected), message);
        }
    }

    /**
     * Asserts that two floats are equal within a delta. If they are not,
     * an AssertionError is thrown. If the expected
     * value is infinity then the delta value is ignored.
     * @param actual the actual value
     * @param expected the expected value
     * @param delta the absolute tolerable difference between the actual and expected values
     */
    static public void assertEquals(float actual, float expected, float delta) {
        assertEquals(actual, expected, delta, null);
    }

    /**
     * Asserts that two longs are equal. If they are not,
     * an AssertionError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(long actual, long expected, String message) {
        assertEquals(Long.valueOf(actual), Long.valueOf(expected), message);
    }

    /**
     * Asserts that two longs are equal. If they are not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(long actual, long expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two booleans are equal. If they are not,
     * an AssertionError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(boolean actual, boolean expected, String message) {
        assertEquals( Boolean.valueOf(actual), Boolean.valueOf(expected), message);
    }

    /**
     * Asserts that two booleans are equal. If they are not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(boolean actual, boolean expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two bytes are equal. If they are not,
     * an AssertionError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(byte actual, byte expected, String message) {
        assertEquals(Byte.valueOf(actual), Byte.valueOf(expected), message);
    }

    /**
     * Asserts that two bytes are equal. If they are not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(byte actual, byte expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two chars are equal. If they are not,
     * an AssertionFailedError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(char actual, char expected, String message) {
        assertEquals(Character.valueOf(actual), Character.valueOf(expected), message);
    }

    /**
     * Asserts that two chars are equal. If they are not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(char actual, char expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two shorts are equal. If they are not,
     * an AssertionFailedError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(short actual, short expected, String message) {
        assertEquals(Short.valueOf(actual), Short.valueOf(expected), message);
    }

    /**
     * Asserts that two shorts are equal. If they are not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(short actual, short expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that two ints are equal. If they are not,
     * an AssertionFailedError, with the given message, is thrown.
     * @param actual the actual value
     * @param expected the expected value
     * @param message the assertion error message
     */
    static public void assertEquals(int actual,  int expected, String message) {
        assertEquals(Integer.valueOf(actual), Integer.valueOf(expected), message);
    }

    /**
     * Asserts that two ints are equal. If they are not,
     * an AssertionError is thrown.
     * @param actual the actual value
     * @param expected the expected value
     */
    static public void assertEquals(int actual, int expected) {
        assertEquals(actual, expected, null);
    }

    /**
     * Asserts that a condition is true. If it isn't, an AssertionError is thrown.
     * @param condition the condition to evaluate
     */
    public static void assertTrue(boolean condition) {
        if (!condition) fail(null);
    }

    /**
     * Asserts that a condition is true. If it isn't,
     * an AssertionError, with the given message, is thrown.
     * @param condition the condition to evaluate
     * @param message the assertion error message
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) fail(message);
    }

    /**
     * Asserts that a condition is false. If it isn't,
     * an AssertionError, with the given message, is thrown.
     * @param condition the condition to evaluate
     * @param message the assertion error message
     */
    public static void assertFalse(boolean condition, String message) {
        if (condition) fail(message);
    }

    /**
     * Fails a test with the given message.
     * @param message the assertion error message
     */
    public static AssertionError fail(String message) {
        throw new AssertionError(message);
    }

    public static void assertEqualsIgnoringOrder(Iterable<?> actual, Iterable<?> expected) {
        assertEqualsIgnoringOrder(actual, expected, false, null);
    }

    public static void assertEqualsIgnoringOrder(Iterable<?> actual, Iterable<?> expected, boolean logDuplicates, String errmsg) {
        Set<?> actualSet = Sets.newLinkedHashSet(actual);
        Set<?> expectedSet = Sets.newLinkedHashSet(expected);
        Set<?> extras = Sets.difference(actualSet, expectedSet);
        Set<?> missing = Sets.difference(expectedSet, actualSet);
        List<Object> duplicates = Lists.newArrayList(actual);
        for (Object a : actualSet) {
            duplicates.remove(a);
        }
        String fullErrmsg = "extras="+extras+"; missing="+missing
                + (logDuplicates ? "; duplicates="+MutableSet.copyOf(duplicates) : "")
                +"; actualSize="+Iterables.size(actual)+"; expectedSize="+Iterables.size(expected)
                +"; actual="+actual+"; expected="+expected+"; "+errmsg;
        assertTrue(extras.isEmpty(), fullErrmsg);
        assertTrue(missing.isEmpty(), fullErrmsg);
        assertTrue(Iterables.size(actual) == Iterables.size(expected), fullErrmsg);
        assertTrue(actualSet.equals(expectedSet), fullErrmsg); // should be covered by extras/missing/size test
    }

    // --- new routines
    
    /**  As {@link #eventually(Supplier, Predicate, Duration, Duration, String)} with defaults. */
    public static <T> void eventually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(supplier, predicate, null, null, null);
    }
    
    /** @deprecated since 0.9.0 use {@link #eventually(Supplier, Predicate, Duration, Duration, String)} */ @Deprecated
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(flags, supplier, predicate, (String)null);
    }
    /** @deprecated since 0.9.0 use {@link #eventually(Supplier, Predicate, Duration, Duration, String)} */ @Deprecated
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        eventually(supplier, predicate, toDuration(flags.get("timeout"), null), toDuration(flags.get("period"), null), errMsg);
    }
    
    /**  As {@link #eventually(Supplier, Predicate, Duration, Duration, String)} with default. */
    public static <T> void eventually(Supplier<? extends T> supplier, Predicate<T> predicate, Duration timeout) {
        eventually(supplier, predicate, timeout, null, null);
    }
    
    /** Asserts that eventually the supplier gives a value accepted by the predicate. 
     * Tests periodically and succeeds as soon as the supplier gives an allowed value.
     * Other arguments can be null.
     * 
     * @param supplier supplies the value to test, such as {@link Suppliers#ofInstance(Object)} for a constant 
     * @param predicate the {@link Predicate} to apply to each value given by the supplier
     * @param timeout how long to wait, default {@link #DEFAULT_SHORT_TIMEOUT}
     * @param period how often to check, default quite often so you won't notice but letting the CPU do work
     * @param errMsg an error message to display if not satisfied, in addition to the last-tested supplied value and the predicate
     */
    public static <T> void eventually(Supplier<? extends T> supplier, Predicate<T> predicate, Duration timeout, Duration period, String errMsg) {
        if (timeout==null) timeout = DEFAULT_SHORT_TIMEOUT;
        if (period==null) period = DEFAULT_SHORT_PERIOD;
        CountdownTimer timeleft = timeout.countdownTimer();
        
        T supplied;
        int count = 0;
        do {
            if (count++ > 0) Duration.sleep(period);
            supplied = supplier.get();
            if (predicate.apply(supplied)) return;
        } while (timeleft.isNotExpired());

        fail("Expected: eventually "+predicate+"; got most recently: "+supplied
            +" (waited "+timeleft.getDurationElapsed()+", checked "+count+")"
            +(errMsg!=null?"; "+errMsg:""));
    }
    
    /**  As {@link #continually(Supplier, Predicate, Duration, Duration, String)} with defaults. */
    public static <T> void continually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        continually(ImmutableMap.<String,Object>of(), supplier, predicate);
    }

    /** @deprecated since 0.9.0 use {@link #eventually(Supplier, Predicate, Duration, Duration, String)} */ @Deprecated
    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<? super T> predicate) {
        continually(flags, supplier, predicate, null);
    }

    /** @deprecated since 0.9.0 use {@link #eventually(Supplier, Predicate, Duration, Duration, String)} */ @Deprecated
    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        continually(supplier, predicate, toDuration(flags.get("timeout"), toDuration(flags.get("duration"), null)), 
            toDuration(flags.get("period"), null), null);
    }
    /** 
     * Asserts that continually the supplier gives a value accepted by the predicate. 
     * Tests periodically and fails if the supplier gives a disallowed value.
     * Other arguments can be null.
     * 
     * @param supplier supplies the value to test, such as {@link Suppliers#ofInstance(Object)} for a constant 
     * @param predicate the {@link Predicate} to apply to each value given by the supplier
     * @param duration how long to test for, default {@link #DEFAULT_SHORT_TIMEOUT}
     * @param period how often to check, default quite often to minimise chance of missing a flashing violation but letting the CPU do work
     * @param errMsg an error message to display if not satisfied, in addition to the last-tested supplied value and the predicate
     */
    public static <T> void continually(Supplier<? extends T> supplier, Predicate<T> predicate, Duration duration, Duration period, String errMsg) {
        if (duration==null) duration = DEFAULT_SHORT_TIMEOUT;
        if (period==null) period = DEFAULT_SHORT_PERIOD;

        CountdownTimer timeleft = duration.countdownTimer();
        
        T supplied;
        int count = 0;
        do {
            if (count > 0) Duration.sleep(period);
            supplied = supplier.get();
            if (!predicate.apply(supplied)) {
                fail("Expected: continually "+predicate+"; got violation: "+supplied
                    // tell timing if it worked the first time and then failed
                    +(count > 0 ? " (after "+timeleft.getDurationElapsed()+", successfully checked "+count+")" : "")
                    +(errMsg!=null?"; "+errMsg:""));
            }
            count++;
        } while (timeleft.isNotExpired());
    }
    
    /**
     * @see #succeedsContinually(Map, Callable)
     */
    public static void succeedsEventually(Runnable r) {
        succeedsEventually(ImmutableMap.<String,Object>of(), r);
    }

    /**
     * @see #succeedsContinually(Map, Callable)
     */
    public static void succeedsEventually(Map<String,?> flags, Runnable r) {
        succeedsEventually(flags, toCallable(r));
    }
    
    /**
     * @see #succeedsContinually(Map, Callable)
     */
    public static <T> T succeedsEventually(Callable<T> c) {
        return succeedsEventually(ImmutableMap.<String,Object>of(), c);
    }
    
    // FIXME duplication with TestUtils.BooleanWithMessage
    public static class BooleanWithMessage {
        boolean value; String message;
        public BooleanWithMessage(boolean value, String message) {
            this.value = value; this.message = message;
        }
        public boolean asBoolean() {
            return value;
        }
        public String toString() {
            return message;
        }
    }

    // TODO flags are ugly; remove this in favour of something strongly typed,
    // e.g. extending Repeater and taking the extra semantics.
    // TODO remove the #succeedsEventually in favour of #eventually (and same for continually)
    /**
     * Convenience method for cases where we need to test until something is true.
     *
     * The Callable will be invoked periodically until it succesfully concludes.
     * <p>
     * The following flags are supported:
     * <ul>
     * <li>abortOnError (boolean, default true)
     * <li>abortOnException - (boolean, default false)
     * <li>timeout - (a Duration or an integer in millis, defaults to {@link Asserts#DEFAULT_LONG_TIMEOUT})
     * <li>period - (a Duration or an integer in millis, for fixed retry time; if not set, defaults to exponentially increasing from 1 to 500ms)
     * <li>minPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the minimum period when exponentially increasing; defaults to 1ms)
     * <li>maxPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the maximum period when exponentially increasing; defaults to 500ms)
     * <li>maxAttempts - (integer, Integer.MAX_VALUE)
     * </ul>
     * 
     * The following flags are deprecated:
     * <ul>
     * <li>useGroovyTruth - (defaults to false; any result code apart from 'false' will be treated as success including null; ignored for Runnables which aren't Callables)
     * </ul>
     * 
     * @param flags, accepts the flags listed above
     * @param c The callable to invoke
     */
    public static <T> T succeedsEventually(Map<String,?> flags, Callable<T> c) {
        boolean abortOnException = get(flags, "abortOnException", false);
        boolean abortOnError = get(flags, "abortOnError", false);
        boolean useGroovyTruth = get(flags, "useGroovyTruth", false);
        boolean logException = get(flags, "logException", true);

        // To speed up tests, default is for the period to start small and increase...
        Duration duration = toDuration(flags.get("timeout"), DEFAULT_LONG_TIMEOUT);
        Duration fixedPeriod = toDuration(flags.get("period"), null);
        Duration minPeriod = (fixedPeriod != null) ? fixedPeriod : toDuration(flags.get("minPeriod"), Duration.millis(1));
        Duration maxPeriod = (fixedPeriod != null) ? fixedPeriod : toDuration(flags.get("maxPeriod"), Duration.millis(500));
        int maxAttempts = get(flags, "maxAttempts", Integer.MAX_VALUE);
        int attempt = 0;
        long startTime = System.currentTimeMillis();
        try {
            Throwable lastException = null;
            T result = null;
            long lastAttemptTime = 0;
            long expireTime = startTime+duration.toMilliseconds();
            long sleepTimeBetweenAttempts = minPeriod.toMilliseconds();
            
            while (attempt < maxAttempts && lastAttemptTime < expireTime) {
                try {
                    attempt++;
                    lastAttemptTime = System.currentTimeMillis();
                    result = c.call();
                    if (log.isTraceEnabled()) log.trace("Attempt {} after {} ms: {}", new Object[] {attempt, System.currentTimeMillis() - startTime, result});
                    if (useGroovyTruth) {
                        if (groovyTruth(result)) return result;
                    } else if (Boolean.FALSE.equals(result)) {
                        if (result instanceof BooleanWithMessage) 
                            log.warn("Test returned an instance of BooleanWithMessage but useGroovyTruth is not set! " +
                                     "The result of this probably isn't what you intended.");
                        // FIXME surprising behaviour, "false" result here is acceptable
                        return result;
                    } else {
                        return result;
                    }
                    lastException = null;
                } catch(Throwable e) {
                    lastException = e;
                    if (log.isTraceEnabled()) log.trace("Attempt {} after {} ms: {}", new Object[] {attempt, System.currentTimeMillis() - startTime, e.getMessage()});
                    if (abortOnException) throw e;
                    if (abortOnError && e instanceof Error) throw e;
                }
                long sleepTime = Math.min(sleepTimeBetweenAttempts, expireTime-System.currentTimeMillis());
                if (sleepTime > 0) Thread.sleep(sleepTime);
                sleepTimeBetweenAttempts = Math.min(sleepTimeBetweenAttempts*2, maxPeriod.toMilliseconds());
            }
            
            log.info("succeedsEventually exceeded max attempts or timeout - {} attempts lasting {} ms, for {}", new Object[] {attempt, System.currentTimeMillis()-startTime, c});
            if (lastException != null)
                throw lastException;
            throw fail("invalid result: "+result);
        } catch (Throwable t) {
            if (logException) log.info("failed succeeds-eventually, "+attempt+" attempts, "+
                    (System.currentTimeMillis()-startTime)+"ms elapsed "+
                    "(rethrowing): "+t);
            throw propagate(t);
        }
    }

    public static <T> void succeedsContinually(Runnable r) {
        succeedsContinually(ImmutableMap.<String,Object>of(), r);
    }
    
    public static <T> void succeedsContinually(Map<?,?> flags, Runnable r) {
        succeedsContinually(flags, toCallable(r));
    }

    public static <T> T succeedsContinually(Callable<T> c) {
        return succeedsContinually(ImmutableMap.<String,Object>of(), c);
    }

    // TODO unify with "continually"; see also eventually, some of those options might be useful
    public static <T> T succeedsContinually(Map<?,?> flags, Callable<T> job) {
        Duration duration = toDuration(flags.get("timeout"), Duration.ONE_SECOND);
        Duration period = toDuration(flags.get("period"), Duration.millis(10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+duration.toMilliseconds();
        int attempt = 0;
        
        boolean first = true;
        T result = null;
        while (first || System.currentTimeMillis() <= expireTime) {
            attempt++;
            try {
                result = job.call();
            } catch (Exception e) {
                log.info("succeedsContinually failed - {} attempts lasting {} ms, for {} (rethrowing)", new Object[] {attempt, System.currentTimeMillis()-startTime, job});
                throw propagate(e);
            }
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
        return result;
    }
    
    private static Duration toDuration(Object duration, Duration defaultVal) {
        if (duration == null)
            return defaultVal;
        else 
            return Duration.of(duration);
    }
    
    public static void assertFails(Runnable r) {
        assertFailsWith(toCallable(r), Predicates.alwaysTrue());
    }
    
    public static void assertFails(Callable<?> c) {
        assertFailsWith(c, Predicates.alwaysTrue());
    }
    
    public static void assertFailsWith(Callable<?> c, final Closure<Boolean> exceptionChecker) {
        assertFailsWith(c, new Predicate<Throwable>() {
            public boolean apply(Throwable input) {
                return exceptionChecker.call(input);
            }
        });
    }
    
    @SafeVarargs
    public static void assertFailsWith(Runnable c, final Class<? extends Throwable> validException, final Class<? extends Throwable> ...otherValidExceptions) {
        final List<Class<?>> validExceptions = ImmutableList.<Class<?>>builder()
                .add(validException)
                .addAll(ImmutableList.copyOf(otherValidExceptions))
                .build();
        
        assertFailsWith(c, new Predicate<Throwable>() {
            public boolean apply(Throwable e) {
                for (Class<?> validException: validExceptions) {
                    if (validException.isInstance(e)) return true;
                }
                fail("Test threw exception of unexpected type "+e.getClass()+"; expecting "+validExceptions);
                return false;
            }
        });
    }

    public static void assertFailsWith(Runnable r, Predicate<? super Throwable> exceptionChecker) {
        assertFailsWith(toCallable(r), exceptionChecker);
    }
    
    public static void assertFailsWith(Callable<?> c, Predicate<? super Throwable> exceptionChecker) {
        boolean failed = false;
        try {
            c.call();
        } catch (Throwable e) {
            failed = true;
            if (!exceptionChecker.apply(e)) {
                log.debug("Test threw invalid exception (failing)", e);
                fail("Test threw invalid exception: "+e);
            }
            log.debug("Test for exception successful ("+e+")");
        }
        if (!failed) fail("Test code should have thrown exception but did not");
    }

    public static void assertReturnsEventually(final Runnable r, Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        Runnable wrappedR = new Runnable() {
            @Override public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    throwable.set(t);
                    throw Exceptions.propagate(t);
                }
            }
        };
        Thread thread = new Thread(wrappedR, "assertReturnsEventually("+r+")");
        try {
            thread.start();
            thread.join(timeout.toMilliseconds());
            if (thread.isAlive()) {
                throw new TimeoutException("Still running: r="+r+"; thread="+Arrays.toString(thread.getStackTrace()));
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            thread.interrupt();
        }
        
        if (throwable.get() !=  null) {
            throw new ExecutionException(throwable.get());
        }
    }

    public static <T> void assertThat(T object, Predicate<T> condition) {
        if (condition.apply(object)) return;
        fail("Failed "+condition+": "+object);
    }

    public static void assertStringContains(String input, String phrase1ToContain, String ...optionalOtherPhrasesToContain) {
        if (input==null) fail("Input is null.");
        if (phrase1ToContain!=null) {
            assertThat(input, StringPredicates.containsLiteral(phrase1ToContain));
        }
        for (String otherPhrase: optionalOtherPhrasesToContain) {
            if (otherPhrase!=null) {
                assertThat(input, StringPredicates.containsLiteral(otherPhrase));
            }
        }
    }
    
    public static void assertStringContainsAtLeastOne(String input, String possiblePhrase1ToContain, String ...optionalOtherPossiblePhrasesToContain) {
        if (input==null) fail("Input is null.");
        List<String> missing = MutableList.of();
        if (possiblePhrase1ToContain!=null) {
            if (input.contains(possiblePhrase1ToContain)) return;
            missing.add(possiblePhrase1ToContain);
        }
        for (String otherPhrase: optionalOtherPossiblePhrasesToContain) {
            if (otherPhrase!=null) {
                if (input.contains(otherPhrase)) return;
                missing.add(otherPhrase);
            }
        }
        fail("Input did not contain any of the expected phrases "+missing+": "+input);
    }
    
    public static void assertStringContainsIgnoreCase(String input, String phrase1ToContain, String ...optionalOtherPhrasesToContain) {
        if (input==null) fail("Input is null.");
        if (phrase1ToContain!=null) {
            assertThat(input, StringPredicates.containsLiteralIgnoreCase(phrase1ToContain));
        }
        for (String otherPhrase: optionalOtherPhrasesToContain) {
            if (otherPhrase!=null) {
                assertThat(input, StringPredicates.containsLiteralIgnoreCase(otherPhrase));
            }
        }
    }
    
    public static void assertStringMatchesRegex(String input, String regex1ToMatch, String ...optionalOtherRegexesToMatch) {
        if (input==null) fail("Input is null.");
        if (regex1ToMatch!=null) {
            assertThat(input, StringPredicates.matchesRegex(regex1ToMatch));
        }
        for (String otherRegex: optionalOtherRegexesToMatch) {
            if (otherRegex!=null) {
                assertThat(input, StringPredicates.matchesRegex(otherRegex));
            }
        }
    }

    /** Subclass of {@link AssertionError} which indicates that code which should have thrown did not, 
     * so that callers can disambiguate expected errors from this one.
     * See {@link #shouldHaveFailedPreviously()} */
    public static class ShouldHaveFailedPreviouslyAssertionError extends AssertionError {
        private static final long serialVersionUID = 4359541529633617518L;
        public ShouldHaveFailedPreviouslyAssertionError() { this("Should have failed previously."); }
        public ShouldHaveFailedPreviouslyAssertionError(String message) { super(message); }
    }
    
    /** Throws a {@link ShouldHaveFailedPreviouslyAssertionError} exception, 
     * to more easily distinguish this failure from other fails.
     * In particular, use one of the <code>expectedFailure</code> methods
     * in the surrounding <code>catch</code> block and this error will pass through it. */
    public static void shouldHaveFailedPreviously() {
        throw new ShouldHaveFailedPreviouslyAssertionError();
    }
    /** As {@link #shouldHaveFailedPreviously()} but allowing detail,
     * for example a value which was received when it shouldn't have been. */
    public static void shouldHaveFailedPreviously(String message) {
        throw new ShouldHaveFailedPreviouslyAssertionError(message);
    }

    /** Tests that an exception is not {@link ShouldHaveFailedPreviouslyAssertionError}.
     * See {@link #shouldHaveFailedPreviously()} */
    public static void expectedFailure(Throwable e) {
        if (e instanceof ShouldHaveFailedPreviouslyAssertionError) throw (Error)e;
    }
    
    /**
     * Tests that an exception is not {@link ShouldHaveFailedPreviouslyAssertionError}
     * and is either one of the given types, or has a caused-by of one of the given types.
     * 
     * If you want *just* the instanceof (without checking the caused-by), then just catch
     * those exception types, treat that as success, and let any other exception be propagated.
     * 
     * @return If the test is satisfied, this method returns normally. 
     * The caller can decide whether anything more should be done with the exception.
     * If the test fails, then either it is propagated, 
     * if the {@link Throwable} is a fatal ({@link Exceptions#propagateIfFatal(Throwable)}) other than an {@link AssertionError}, 
     * or more usually the test failure of this method is thrown, 
     * with detail of the original {@link Throwable} logged and included in the caused-by.
     */
    public static void expectedFailureOfType(Throwable e, Class<?> ...permittedSupertypes) {
        if (e instanceof ShouldHaveFailedPreviouslyAssertionError) throw (Error)e;
        for (Class<?> clazz: permittedSupertypes) {
            @SuppressWarnings("unchecked")
            Throwable match = Exceptions.getFirstThrowableOfType(e, (Class<? extends Throwable>)clazz);
            if (match != null) return;
        }
        rethrowPreferredException(e, 
            new AssertionError("Error "+JavaClassNames.simpleClassName(e)+" is not any of the expected types: " + Arrays.asList(permittedSupertypes), e));
    }
    
    /** Tests {@link #expectedFailure(Throwable)} and that the <code>toString</code>
     * satisfies {@link #assertStringContains(String, String, String...)}.
     * @return as per {@link #expectedFailureOfType(Throwable, Class...)} */
    public static void expectedFailureContains(Throwable e, String phrase1ToContain, String ...optionalOtherPhrasesToContain) {
        if (e instanceof ShouldHaveFailedPreviouslyAssertionError) throw (Error)e;
        try {
            assertStringContains(e.toString(), phrase1ToContain, optionalOtherPhrasesToContain);
        } catch (AssertionError ee) {
            rethrowPreferredException(e, ee);
        }
    }

    /** As {@link #expectedFailureContains(Throwable, String, String...)} but case insensitive */
    public static void expectedFailureContainsIgnoreCase(Throwable e, String phrase1ToContain, String ...optionalOtherPhrasesToContain) {
        if (e instanceof ShouldHaveFailedPreviouslyAssertionError) throw (Error)e;
        try {
            assertStringContainsIgnoreCase(e.toString(), phrase1ToContain, optionalOtherPhrasesToContain);
        } catch (AssertionError ee) {
            rethrowPreferredException(e, ee);
        }
    }

    /** Implements the return beahvior for {@link #expectedFailureOfType(Throwable, Class...)} and others. */
    private static void rethrowPreferredException(Throwable earlierPreferredIfFatalElseLogged, Throwable laterPreferredOtherwise) throws AssertionError {
        if (!(earlierPreferredIfFatalElseLogged instanceof AssertionError)) {
            Exceptions.propagateIfFatal(earlierPreferredIfFatalElseLogged);
        }
        log.warn("Detail of unexpected error: "+earlierPreferredIfFatalElseLogged, earlierPreferredIfFatalElseLogged);
        throw Exceptions.propagate(laterPreferredOtherwise);
    }

    @SuppressWarnings("rawtypes")
    private static boolean groovyTruth(Object o) {
        // TODO Doesn't handle matchers (see http://docs.codehaus.org/display/GROOVY/Groovy+Truth)
        if (o == null) {
            return false;
        } else if (o instanceof Boolean) {
            return (Boolean)o;
        } else if (o instanceof String) {
            return !((String)o).isEmpty();
        } else if (o instanceof Collection) {
            return !((Collection)o).isEmpty();
        } else if (o instanceof Map) {
            return !((Map)o).isEmpty();
        } else if (o instanceof Iterator) {
            return ((Iterator)o).hasNext();
        } else if (o instanceof Enumeration) {
            return ((Enumeration)o).hasMoreElements();
        } else {
            return true;
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T get(Map<String,?> map, String key, T defaultVal) {
        Object val = map.get(key);
        return (T) ((val == null) ? defaultVal : val);
    }
    
    private static Callable<?> toCallable(Runnable r) {
        return (r instanceof Callable) ? (Callable<?>)r : new RunnableAdapter<Void>(r, null);
    }
    
    /** Same as {@link java.util.concurrent.Executors#callable(Runnable)}, except includes toString() */
    static final class RunnableAdapter<T> implements Callable<T> {
        final Runnable task;
        final T result;
        RunnableAdapter(Runnable task, T result) {
            this.task = task;
            this.result = result;
        }
        public T call() {
            task.run();
            return result;
        }
        @Override
        public String toString() {
            return "RunnableAdapter("+task+")";
        }
    }
    
    private static void sleep(long periodMs) {
        if (periodMs > 0) {
            try {
                Thread.sleep(periodMs);
            } catch (InterruptedException e) {
                throw propagate(e);
            }
        }
    }
    
    private static RuntimeException propagate(Throwable t) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (t instanceof RuntimeException) throw (RuntimeException)t;
        if (t instanceof Error) throw (Error)t;
        throw new RuntimeException(t);
    }

    /** As {@link #eventuallyOnNotify(Object, Supplier, Predicate, Duration, boolean)} with default timeout. */
    public static <T> void eventuallyOnNotify(Object notifyTarget, Supplier<T> supplier, Predicate<T> predicate) {
        eventuallyOnNotify(notifyTarget, supplier, predicate, null);
    }
    
    /** as {@link #eventually(Supplier, Predicate)} for cases where an object is notified;
     * more efficient as it waits on the notify target object. 
     * See also the simpler {@link #eventuallyOnNotify(Object, Predicate)} when looking at a collection which is getting notified.
     * Timeout defaults to {@link #DEFAULT_SHORT_TIMEOUT}. 
     * <p>
     * This synchronizes on the notify target for the duration of the wait, 
     * including while getting and checking the value, so as not to miss any notification. */
    public static <T> void eventuallyOnNotify(Object notifyTarget, Supplier<T> supplier, Predicate<T> predicate, Duration timeout) {
        T supplied = null;
        if (timeout==null) timeout = DEFAULT_SHORT_TIMEOUT;
        CountdownTimer remaining = timeout.countdownTimer();
        int checks = 0;
        synchronized (notifyTarget) {
            do {
                if (checks>0) {
                    remaining.waitOnForExpiryUnchecked(notifyTarget);
                }
                supplied = supplier.get();
                if (predicate.apply(supplied)) return;
                checks++;
            } while (remaining.isNotExpired());
        }
        
        // should get 2 checks, 1 before and 1 after, if no notifications; if more, tell the user
        fail("Expected: eventually "+predicate+"; got most recently: "+supplied+
            " (waited "+remaining.getDurationElapsed()+
                (checks>2 ? "; notification count "+(checks-2) : "")+
            ")");
    }

    /** Convenience for {@link #eventuallyOnNotify(Object, Supplier, Predicate, Duration, boolean)} 
     * when the notify target and the value under test are the same. */
    public static <T> void eventuallyOnNotify(T object, Predicate<T> predicate, Duration timeout) {
        eventuallyOnNotify(object, Suppliers.ofInstance(object), predicate, timeout);
    }

    /** As {@link #eventuallyOnNotify(Object, Predicate, Duration)} with the default duration of {@link #eventuallyOnNotify(Object, Supplier, Predicate)}. */
    public static <T> void eventuallyOnNotify(T object, Predicate<T> predicate) {
        eventuallyOnNotify(object, Suppliers.ofInstance(object), predicate, null);
    }

}
