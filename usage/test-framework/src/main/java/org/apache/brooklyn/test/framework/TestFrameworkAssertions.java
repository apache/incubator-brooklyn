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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class to evaluate test-framework assertions
 *
 * @author m4rkmckenna on 11/11/2015.
 */
public class TestFrameworkAssertions {
    private static final Logger LOG = LoggerFactory.getLogger(TestFrameworkAssertions.class);

    private TestFrameworkAssertions() {
    }

    /**
     * Evaluates all assertions against dataSupplier
     *
     * @param dataSupplier
     * @param flags
     * @param assertions
     */
    public static void checkAssertions(final Supplier<String> dataSupplier, final Map flags, final List<Map<String, Object>> assertions) {
        //Iterate through assert array
        for (final Map<String, Object> assertionsMap : assertions) {
            checkAssertions(dataSupplier, flags, assertionsMap);
        }
    }

    /**
     * Evaluates all assertions against dataSupplier
     *
     * @param dataSupplier
     * @param flags
     * @param assertionsMap
     */
    public static void checkAssertions(final Supplier<String> dataSupplier, final Map flags, final Map<String, Object> assertionsMap) {
        for (final Map.Entry<String, Object> assertion : assertionsMap.entrySet()) {
            final Maybe<Predicate<String>> optionalPredicate = getPredicate(assertion.getKey(), assertion.getValue());
            Asserts.succeedsEventually(flags, new PredicateChecker(dataSupplier, optionalPredicate.get()));
        }
    }

    /**
     * Returns the predicate associated with the predicateKey if one exists
     *
     * @param predicateKey
     * @param predicateTarget
     * @return {@link Maybe} of {@Link Predicate}
     */
    public static Maybe<Predicate<String>> getPredicate(final String predicateKey, final Object predicateTarget) {
        if (StringUtils.equalsIgnoreCase("isNull", predicateKey)) {
            return Maybe.of(Predicates.<String>isNull());
        } else if (StringUtils.equalsIgnoreCase("notNull", predicateKey)) {
            return Maybe.of(Predicates.<String>notNull());
        } else if (StringUtils.equalsIgnoreCase("isEqualTo", predicateKey)
                || StringUtils.equalsIgnoreCase("equalTo", predicateKey)
                || StringUtils.equalsIgnoreCase("equals", predicateKey)) {
            return Maybe.of(Predicates.equalTo(TypeCoercions.coerce(predicateTarget.toString(), String.class)));
        } else if (StringUtils.equalsIgnoreCase("matches", predicateKey)) {
            return Maybe.of(buildMatchesPredicate(TypeCoercions.coerce(predicateTarget, String.class)));
        } else if (StringUtils.equalsIgnoreCase("contains", predicateKey)) {
            return Maybe.of(buildContainsPredicate(TypeCoercions.coerce(predicateTarget, String.class)));
        }
        return Maybe.absent(String.format("No predicate found with signature [%s]", predicateKey));
    }

    /**
     * Builds a predicate that checks if a string contains the supplied value
     *
     * @param predicateTarget
     * @return {@link Predicate}
     */
    private static Predicate<String> buildContainsPredicate(final String predicateTarget) {
        return new Predicate<String>() {

            @Override
            public boolean apply(@Nullable final String input) {
                return StringUtils.contains(input, predicateTarget);
            }

            @Override
            public String toString() {
                return String.format("TestFrameworkAssertions.contains(%s)", predicateTarget);
            }
        };
    }

    /**
     * Builds a predicate that checks if a string matches the supplied pattern
     *
     * @param predicateTarget The pattern to check
     * @return {@link Predicate}
     */
    private static Predicate<String> buildMatchesPredicate(final String predicateTarget) {
        final Pattern pattern = Pattern.compile(predicateTarget);
        return new Predicate<String>() {
            public boolean apply(final String input) {
                return (input != null) && pattern.matcher(input.toString()).matches();
            }

            @Override
            public String toString() {
                return String.format("TestFrameworkAssertions.matches(%s)", predicateTarget);
            }
        };
    }

    /**
     * Runnable that will be passed to {@link Asserts#succeedsEventually}
     */
    private static class PredicateChecker implements Runnable {
        private final Supplier<String> dataSupplier;
        private final Predicate<String> predicate;

        public PredicateChecker(final Supplier<String> dataSupplier, final Predicate<String> predicate) {
            this.dataSupplier = dataSupplier;
            this.predicate = predicate;
        }

        @Override
        public void run() {
            final String value = dataSupplier.get();
            LOG.debug("Evaluating predicate [{}] with value [{}]", predicate.toString(), value);
            Asserts.assertEquals(predicate.apply(value), true);
            LOG.debug("Evaluation of predicate [{}] with value [{}] ... PASSED", predicate.toString(), value);
        }
    }
}
