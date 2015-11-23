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
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Duration;
import org.python.google.common.collect.ImmutableMap;
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

import static org.assertj.core.api.Assertions.assertThat;

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
                {"some-sensor-value", Arrays.asList(ImmutableMap.of("equals", "some-sensor-value"))},
                {"some-regex-value-to-match", Arrays.asList(ImmutableMap.of("matches", "some.*match", "isEqualTo", "some-regex-value-to-match"))},
                {null, Arrays.asList(ImmutableMap.of("isNUll", ""))},
                {"some-non-null-value", Arrays.asList(ImmutableMap.of("notNull", ""))},
                {"<html><body><h1>Im a H1 tag!</h1></body></html>", Arrays.asList(ImmutableMap.of("contains", "Im a H1 tag!"))},
                {"{\"a\":\"b\",\"c\":\"d\",\"e\":123,\"g\":false}", Arrays.asList(ImmutableMap.of("contains", "false"))}
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
        TestFrameworkAssertions.checkAssertions(supplier, ImmutableMap.of("timeout", new Duration(2L, TimeUnit.SECONDS)), assertions);
    }

    @DataProvider
    public Object[][] negativeTestsDP() {
        return new Object[][]{
                {"some-sensor-value", Arrays.asList(ImmutableMap.of("isEqualTo", Identifiers.makeRandomId(8)))},
                {"some-sensor-value", Arrays.asList(ImmutableMap.of("equals", Identifiers.makeRandomId(8)))},
                {"some-regex-value-to-match", Arrays.asList(ImmutableMap.of("matches", "some.*not-match", "isEqualTo", "oink"))},
                {null, Arrays.asList(ImmutableMap.of("notNull", ""))},
                {"some-non-null-value", Arrays.asList(ImmutableMap.of("isNull", ""))},
                {"<html><body><h1>Im a H1 tag!</h1></body></html>", Arrays.asList(ImmutableMap.of("contains", "quack"))},
                {"{\"a\":\"b\",\"c\":\"d\",\"e\":123,\"g\":false}", Arrays.asList(ImmutableMap.of("contains", "moo"))}
        };
    }

    @Test(dataProvider = "negativeTestsDP")
    public void negativeTests(final String data, final List<Map<String, Object>> assertions) {
        final Supplier<String> supplier = new Supplier<String>() {
            @Override
            public String get() {
                LOG.info("Supplier invoked for data [{}]", data);
                return data;
            }
        };
        boolean assertionErrorCaught = false;
        try {
            TestFrameworkAssertions.checkAssertions(supplier, ImmutableMap.of("timeout", new Duration(2L, TimeUnit.SECONDS)), assertions);
        } catch (AssertionError e) {
            assertionErrorCaught = true;
            assertThat(e).hasMessage("expected [true] but found [false]");
        } finally {
            assertThat(assertionErrorCaught).isTrue().as("An assertion error should have been thrown");
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
        boolean illegalStateExceptionThrown = false;
        try {
            TestFrameworkAssertions.checkAssertions(supplier, ImmutableMap.of("timeout", new Duration(2L, TimeUnit.SECONDS)), assertions);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(IllegalStateException.class);
            assertThat(e).hasMessage("No predicate found with signature [" + randomId + "]");
            illegalStateExceptionThrown = true;
        } finally {
            assertThat(illegalStateExceptionThrown).isTrue().as("An illegal state exception should have been thrown");
        }
    }


}
