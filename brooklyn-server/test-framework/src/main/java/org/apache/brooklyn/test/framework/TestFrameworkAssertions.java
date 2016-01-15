/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;
import org.apache.brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;


/**
 * Utility class to evaluate test-framework assertions
 *
 * @author m4rkmckenna on 11/11/2015.
 */
public class TestFrameworkAssertions {

    public static final String IS_NULL = "isNull";
    public static final String NOT_NULL = "notNull";
    public static final String IS_EQUAL_TO = "isEqualTo";
    public static final String EQUAL_TO = "equalTo";
    public static final String EQUALS = "equals";
    public static final String MATCHES = "matches";
    public static final String CONTAINS = "contains";
    public static final String IS_EMPTY = "isEmpty";
    public static final String NOT_EMPTY = "notEmpty";
    public static final String HAS_TRUTH_VALUE = "hasTruthValue";
    public static final String UNKNOWN_CONDITION = "unknown condition";


    private TestFrameworkAssertions() {
    }


    /**
     *  Get assertions tolerantly from a configuration key.
     *  This supports either a simple map of assertions, such as
     *
     <pre>
     assertOut:
       contains: 2 users
       matches: .*[\d]* days.*
     </pre>
     * or a list of such maps, (which allows you to repeat keys):
     <pre>
     assertOut:
     - contains: 2 users
     - contains: 2 days
     </pre>
     or
    private static List<Map<String,Object>> getAssertions(ConfigKey<Object> key) {
    }
    */
    public static List<Map<String, Object>> getAssertions(Entity entity, ConfigKey<Object> key) {
        Object config = entity.getConfig(key);
        Maybe<Map<String, Object>> maybeMap = TypeCoercions.tryCoerce(config, new TypeToken<Map<String, Object>>() {});
        if (maybeMap.isPresent()) {
            return Collections.singletonList(maybeMap.get());
        }

        Maybe<List<Map<String, Object>>> maybeList = TypeCoercions.tryCoerce(config,
            new TypeToken<List<Map<String, Object>>>() {});
        if (maybeList.isPresent()) {
            return maybeList.get();
        }

        throw new FatalConfigurationRuntimeException(key.getDescription() + " is not a map or list of maps");
    }


    public static <T> void checkAssertions(Map<String,?> flags,
                                           Map<String, Object> assertions,
                                           String target,
                                           final Supplier<T> actualSupplier) {

        AssertionSupport support = new AssertionSupport();
        checkAssertions(support, flags, assertions, target, actualSupplier);
        support.validate();
    }


    public static <T> void checkAssertions(Map<String,?> flags,
                                           List<Map<String, Object>> assertions,
                                           String target,
                                           final Supplier<T> actualSupplier) {

        AssertionSupport support = new AssertionSupport();
        for (Map<String, Object> assertionMap : assertions) {
            checkAssertions(support, flags, assertionMap, target, actualSupplier);
        }
        support.validate();
    }

    public static <T> void checkAssertions(final AssertionSupport support,
                                           Map<String,?> flags,
                                           final List<Map<String, Object>> assertions,
                                           final String target,
                                           final Supplier<T> actualSupplier) {

        for (Map<String, Object> assertionMap : assertions) {
            checkAssertions(support, flags, assertionMap, target, actualSupplier);
        }
    }

    public static <T> void checkAssertions(final AssertionSupport support,
                                           Map<String,?> flags,
                                           final Map<String, Object> assertions,
                                           final String target,
                                           final Supplier<T> actualSupplier) {

        if (null == assertions) {
            return;
        }
        try {
            Asserts.succeedsEventually(flags, new Runnable() {
                @Override
                public void run() {
                    T actual = actualSupplier.get();
                    checkActualAgainstAssertions(assertions, target, actual);
                }
            });
        } catch (Throwable t) {
            support.fail(t);
        }
    }

    private static <T> void checkActualAgainstAssertions(Map<String, Object> assertions,
                                                         String target, T actual) {
        for (Map.Entry<String, Object> assertion : assertions.entrySet()) {
            String condition = assertion.getKey().toString();
            Object expected = assertion.getValue();
            switch (condition) {

                case IS_EQUAL_TO :
                case EQUAL_TO :
                case EQUALS :
                    if (null == actual || !actual.equals(expected)) {
                        failAssertion(target, EQUALS, expected);
                    }
                    break;

                case IS_NULL :
                    if (isTrue(expected) != (null == actual)) {
                        failAssertion(target, IS_NULL, expected);
                    }
                    break;

                case NOT_NULL :
                    if (isTrue(expected) != (null != actual)) {
                        failAssertion(target, NOT_NULL, expected);
                    }
                    break;

                case CONTAINS :
                    if (null == actual || !actual.toString().contains(expected.toString())) {
                        failAssertion(target, CONTAINS, expected);
                    }
                    break;

                case IS_EMPTY :
                    if (isTrue(expected) != (null == actual || Strings.isEmpty(actual.toString()))) {
                        failAssertion(target, IS_EMPTY, expected);
                    }
                    break;

                case NOT_EMPTY :
                    if (isTrue(expected) != ((null != actual && Strings.isNonEmpty(actual.toString())))) {
                        failAssertion(target, NOT_EMPTY, expected);
                    }
                    break;

                case MATCHES :
                    if (null == actual || !actual.toString().matches(expected.toString())) {
                        failAssertion(target, MATCHES, expected);
                    }
                    break;

                case HAS_TRUTH_VALUE :
                    if (isTrue(expected) != isTrue(actual)) {
                        failAssertion(target, HAS_TRUTH_VALUE, expected);
                    }
                    break;

                default:
                    failAssertion(target, UNKNOWN_CONDITION, condition);
            }
        }
    }

    static void failAssertion(String target, String assertion, Object expected) {
        throw new AssertionError(Joiner.on(' ').join(
            null != target ? target : "null",
            null != assertion ? assertion : "null",
            null != expected ? expected : "null"));
    }

    private static boolean isTrue(Object object) {
        return null != object && Boolean.valueOf(object.toString());
    }

    /**
     * A convenience to collect multiple assertion failures.
     */
    public static class AssertionSupport {
        private List<AssertionError> failures = new ArrayList<>();

        public void fail(String target, String assertion, Object expected) {
            failures.add(new AssertionError(Joiner.on(' ').join(
                null != target ? target : "null",
                null != assertion ? assertion : "null",
                null != expected ? expected : "null")));
        }

        public void fail(Throwable throwable) {
            failures.add(new AssertionError(throwable.getMessage(), throwable));
        }

        /**
         * @throws AssertionError if any failures were collected.
         */
        public void validate() {
            if (0 < failures.size()) {

                if (1 == failures.size()) {
                    throw failures.get(0);
                }

                StringBuilder builder = new StringBuilder();
                for (AssertionError assertionError : failures) {
                    builder.append(assertionError.getMessage()).append("\n");
                }
                throw new AssertionError("Assertions failed:\n" + builder, new CompoundRuntimeException("Assertions", failures));
            }
        }
    }
}
