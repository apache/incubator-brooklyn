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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * @author m4rkmckenna on 11/11/2015.
 */
public class TestFrameworkAssertionsTest {
    private static final Logger LOG = LoggerFactory.getLogger(TestFrameworkAssertionsTest.class);

    @BeforeMethod
    public void setup() {

    }

    @DataProvider
    public Object[][] positiveTestsDP() {
        return new Object[][]{
                {"some-sensor-value", Arrays.asList(ImmutableMap.of("isEqualTo", "some-sensor-value"))},
                {"some-sensor-value", Arrays.asList(ImmutableMap.of("equalTo", "some-sensor-value"))},
                {"some-sensor-value", Arrays.asList(ImmutableMap.of("equals", "some-sensor-value"))},
                {"some-regex-value-to-match", Arrays.asList(ImmutableMap.of("matches", "some.*match", "isEqualTo", "some-regex-value-to-match"))},
                {null, Arrays.asList(ImmutableMap.of("isNull", Boolean.TRUE))},
                {"some-non-null-value", Arrays.asList(ImmutableMap.of("isNull", Boolean.FALSE))},
                {null, Arrays.asList(ImmutableMap.of("notNull", Boolean.FALSE))},
                {"some-non-null-value", Arrays.asList(ImmutableMap.of("notNull", Boolean.TRUE))},
                {"<html><body><h1>Im a H1 tag!</h1></body></html>", Arrays.asList(ImmutableMap.of("contains", "Im a H1 tag!"))},
                {"{\"a\":\"b\",\"c\":\"d\",\"e\":123,\"g\":false}", Arrays.asList(ImmutableMap.of("contains", "false"))},
                {"", Arrays.asList(ImmutableMap.of("isEmpty", Boolean.TRUE))},
                {"some-non-null-value", Arrays.asList(ImmutableMap.of("isEmpty", Boolean.FALSE))},
                {null, Arrays.asList(ImmutableMap.of("notEmpty", Boolean.FALSE))},
                {"some-non-null-value", Arrays.asList(ImmutableMap.of("notEmpty", Boolean.TRUE))},
                {"true", Arrays.asList(ImmutableMap.of("hasTruthValue", Boolean.TRUE))},
                {"false", Arrays.asList(ImmutableMap.of("hasTruthValue", Boolean.FALSE))},
                {"some-non-null-value", Arrays.asList(ImmutableMap.of("hasTruthValue", Boolean.FALSE))},
        };
    }

    @Test(dataProvider = "positiveTestsDP")
    public void positiveTest(final String data, final List<Map<String, Object>> assertions) {
        final Supplier<String> supplier = new Supplier<String>() {
            @Override
            public String get() {
                LOG.info("Supplier invoked for data [{}]", data);
                return data;
            }
        };
        TestFrameworkAssertions.checkAssertions(ImmutableMap.of("timeout", new Duration(2L, TimeUnit.SECONDS)), assertions, data, supplier);
    }

    @DataProvider
    public Object[][] negativeTestsDP() {
        String arbitrary = Identifiers.makeRandomId(8);
        return new Object[][]{
                {"some-sensor-value", "equals", arbitrary, Arrays.asList(ImmutableMap.of("isEqualTo", arbitrary))},
                {"some-sensor-value", "equals", arbitrary, Arrays.asList(ImmutableMap.of("equalTo", arbitrary))},
                {"some-sensor-value", "equals", arbitrary, Arrays.asList(ImmutableMap.of("equals", arbitrary))},

                {"some-regex-value-to-match", "matches", "some.*not-match", Arrays.asList(ImmutableMap.of("matches", "some.*not-match", "isEqualTo", "oink"))},

                {null, "notNull", Boolean.TRUE, Arrays.asList(ImmutableMap.of("notNull", Boolean.TRUE))},
                {"some-not-null-value", "notNull", Boolean.FALSE, Arrays.asList(ImmutableMap.of("notNull", Boolean.FALSE))},
                {"some-non-null-value", "isNull", Boolean.TRUE, Arrays.asList(ImmutableMap.of("isNull", Boolean.TRUE))},
                {null, "isNull", Boolean.FALSE, Arrays.asList(ImmutableMap.of("isNull", Boolean.FALSE))},

                {null, "notEmpty", Boolean.TRUE, Arrays.asList(ImmutableMap.of("notEmpty", Boolean.TRUE))},
                {"some-not-null-value", "notEmpty", Boolean.FALSE, Arrays.asList(ImmutableMap.of("notEmpty", Boolean.FALSE))},
                {"some-non-null-value", "isEmpty", Boolean.TRUE, Arrays.asList(ImmutableMap.of("isEmpty", Boolean.TRUE))},
                {null, "isEmpty", Boolean.FALSE, Arrays.asList(ImmutableMap.of("isEmpty", Boolean.FALSE))},

                {"<html><body><h1>Im a H1 tag!</h1></body></html>", "contains", "quack", Arrays.asList(ImmutableMap.of("contains", "quack"))},
                {"{\"a\":\"b\",\"c\":\"d\",\"e\":123,\"g\":false}", "contains", "moo", Arrays.asList(ImmutableMap.of("contains", "moo"))},

                {"true", "hasTruthValue", Boolean.FALSE, Arrays.asList(ImmutableMap.of("hasTruthValue", Boolean.FALSE))},
                {"false", "hasTruthValue", Boolean.TRUE, Arrays.asList(ImmutableMap.of("hasTruthValue", Boolean.TRUE))},
                {"some-not-null-value", "hasTruthValue", Boolean.TRUE, Arrays.asList(ImmutableMap.of("hasTruthValue", Boolean.TRUE))}
        };
    }

    @Test(dataProvider = "negativeTestsDP")
    public void negativeTests(final String data, String condition, Object expected, final List<Map<String, Object>> assertions) {
        final Supplier<String> supplier = new Supplier<String>() {
            @Override
            public String get() {
                LOG.info("Supplier invoked for data [{}]", data);
                return data;
            }
        };
        try {
            TestFrameworkAssertions.checkAssertions(ImmutableMap.of("timeout", new Duration(2L, TimeUnit.SECONDS)), assertions, data, supplier);
            Asserts.shouldHaveFailedPreviously();
        } catch (AssertionError e) {
            Asserts.expectedFailureContains(e, data, condition, expected.toString());
        }

    }

    @Test
    public void testUnknownAssertion() {
        final String randomId = Identifiers.makeRandomId(8);
        final Map<String, Object> assertions = new HashMap<>();
        assertions.put(randomId, randomId);

        final Supplier<String> supplier = new Supplier<String>() {
            @Override
            public String get() {
                LOG.info("Supplier invoked for data [{}]", randomId);
                return randomId;
            }
        };
        try {
            TestFrameworkAssertions.checkAssertions(ImmutableMap.of("timeout", new Duration(2L, TimeUnit.SECONDS)), assertions, "anyTarget", supplier);
            Asserts.shouldHaveFailedPreviously();
        } catch (Throwable e) {
            Asserts.expectedFailureOfType(e, AssertionError.class);
            Asserts.expectedFailureContains(e, TestFrameworkAssertions.UNKNOWN_CONDITION);
        }
    }


}
