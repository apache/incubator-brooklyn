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
package org.apache.brooklyn.util.core.flags;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.util.core.flags.MethodCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.testng.Assert.*;

public class MethodCoercionsTest {

    private Method singleParameterMethod;
    private Method multiParameterMethod;
    private Method singleCollectionParameterMethod;

    @BeforeClass
    public void testFixtureSetUp() {
        try {
            singleParameterMethod = TestClass.class.getMethod("singleParameterMethod", int.class);
            multiParameterMethod = TestClass.class.getMethod("multiParameterMethod", boolean.class, int.class);
            singleCollectionParameterMethod = TestClass.class.getMethod("singleCollectionParameterMethod", List.class);
        } catch (NoSuchMethodException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Test
    public void testMatchSingleParameterMethod() throws Exception {
        Predicate<Method> predicate = MethodCoercions.matchSingleParameterMethod("singleParameterMethod", "42");
        assertTrue(predicate.apply(singleParameterMethod));
        assertFalse(predicate.apply(multiParameterMethod));
        assertFalse(predicate.apply(singleCollectionParameterMethod));
    }

    @Test
    public void testTryFindAndInvokeSingleParameterMethod() throws Exception {
        TestClass instance = new TestClass();
        Maybe<?> maybe = MethodCoercions.tryFindAndInvokeSingleParameterMethod(instance, "singleParameterMethod", "42");
        assertTrue(maybe.isPresent());
        assertTrue(instance.wasSingleParameterMethodCalled());
    }

    @Test
    public void testMatchMultiParameterMethod() throws Exception {
        Predicate<Method> predicate = MethodCoercions.matchMultiParameterMethod("multiParameterMethod", ImmutableList.of("true", "42"));
        assertFalse(predicate.apply(singleParameterMethod));
        assertTrue(predicate.apply(multiParameterMethod));
        assertFalse(predicate.apply(singleCollectionParameterMethod));
    }

    @Test
    public void testTryFindAndInvokeMultiParameterMethod() throws Exception {
        TestClass instance = new TestClass();
        Maybe<?> maybe = MethodCoercions.tryFindAndInvokeMultiParameterMethod(instance, "multiParameterMethod", ImmutableList.of("true", "42"));
        assertTrue(maybe.isPresent());
        assertTrue(instance.wasMultiParameterMethodCalled());
    }

    @Test
    public void testTryFindAndInvokeBestMatchingMethod() throws Exception {
        TestClass instance = new TestClass();
        Maybe<?> maybe = MethodCoercions.tryFindAndInvokeBestMatchingMethod(instance, "singleParameterMethod", "42");
        assertTrue(maybe.isPresent());
        assertTrue(instance.wasSingleParameterMethodCalled());

        instance = new TestClass();
        maybe = MethodCoercions.tryFindAndInvokeBestMatchingMethod(instance, "multiParameterMethod", ImmutableList.of("true", "42"));
        assertTrue(maybe.isPresent());
        assertTrue(instance.wasMultiParameterMethodCalled());

        instance = new TestClass();
        maybe = MethodCoercions.tryFindAndInvokeBestMatchingMethod(instance, "singleCollectionParameterMethod", ImmutableList.of("fred", "joe"));
        assertTrue(maybe.isPresent());
        assertTrue(instance.wasSingleCollectionParameterMethodCalled());
    }
/*
    @Test
    public void testMatchSingleCollectionParameterMethod() throws Exception {
        Predicate<Method> predicate = MethodCoercions.matchSingleCollectionParameterMethod("singleCollectionParameterMethod", ImmutableList.of("42"));
        assertFalse(predicate.apply(singleParameterMethod));
        assertFalse(predicate.apply(multiParameterMethod));
        assertTrue(predicate.apply(singleCollectionParameterMethod));
    }

    @Test
    public void testTryFindAndInvokeSingleCollectionParameterMethod() throws Exception {
        TestClass instance = new TestClass();
        Maybe<?> maybe = MethodCoercions.tryFindAndInvokeSingleCollectionParameterMethod(instance, "singleCollectionParameterMethod", ImmutableList.of("42"));
        assertTrue(maybe.isPresent());
        assertTrue(instance.wasSingleCollectionParameterMethodCalled());
    }
*/
    public static class TestClass {

        private boolean singleParameterMethodCalled;
        private boolean multiParameterMethodCalled;
        private boolean singleCollectionParameterMethodCalled;

        public void singleParameterMethod(int parameter) {
            singleParameterMethodCalled = true;
        }

        public void multiParameterMethod(boolean parameter1, int parameter2) {
            multiParameterMethodCalled = true;
        }

        public void singleCollectionParameterMethod(List<String> parameter) {
            singleCollectionParameterMethodCalled = true;
        }

        public boolean wasSingleParameterMethodCalled() {
            return singleParameterMethodCalled;
        }

        public boolean wasMultiParameterMethodCalled() {
            return multiParameterMethodCalled;
        }

        public boolean wasSingleCollectionParameterMethodCalled() {
            return singleCollectionParameterMethodCalled;
        }
    }
}