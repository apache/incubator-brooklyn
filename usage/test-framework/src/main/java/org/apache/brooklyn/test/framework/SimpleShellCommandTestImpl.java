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
package org.apache.brooklyn.test.framework;


import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.test.Asserts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.apache.brooklyn.util.groovy.GroovyJavaMethods.truth;
import static org.apache.commons.collections.MapUtils.isEmpty;

public class SimpleShellCommandTestImpl extends SimpleShellCommandImpl implements SimpleShellCommandTest {

    public static final int SUCCESS = 0;

    @Override
    public Entity resolveTarget() {
        return AbstractTest.resolveTarget(getExecutionContext(), this);
    }

    /**
     * The test will choose the location of its target entity.
     */
    public Collection<? extends Location> filterLocations(Collection<? extends Location> locations) {
        Entity target = resolveTarget();
        return target.getLocations();
    }

    @Override
    protected void handle(SimpleShellCommand.Result result) {
        AssertionSupport support = new AssertionSupport();
        checkAssertions(support, exitCodeAssertions(), "exit code", result.getExitCode());
        checkAssertions(support, getConfig(ASSERT_OUT), "stdout", result.getStdout());
        checkAssertions(support, getConfig(ASSERT_ERR), "stderr", result.getStderr());
        support.validate();
    }

    private <T> void checkAssertions(AssertionSupport support, Map<?, ?> assertions, String target, T actual) {
        if (null == assertions) {
            return;
        }
        if (null == actual) {
            support.fail(target, "no actual value", "");
            return;
        }
        for (Map.Entry<?, ?> assertion : assertions.entrySet()) {
            String condition = assertion.getKey().toString();
            Object expected = assertion.getValue();
            switch (condition) {
                case EQUALS :
                    if (!actual.equals(expected)) {
                        support.fail(target, EQUALS, expected);
                    }
                    break;
                case CONTAINS :
                    if (!actual.toString().contains(expected.toString())) {
                        support.fail(target, CONTAINS, expected);
                    }
                    break;
                case IS_EMPTY:
                    if (!actual.toString().isEmpty() && truth(expected)) {
                        support.fail(target, IS_EMPTY, expected);
                    }
                    break;
                case MATCHES :
                    if (!actual.toString().matches(expected.toString())) {
                        support.fail(target, MATCHES, expected);
                    }
                    break;
                default:
                    support.fail(target, "unknown condition", condition);
            }
        }
    }

    private Map<?, ?> exitCodeAssertions() {
        Map<?, ?> assertStatus = getConfig(ASSERT_STATUS);
        if (isEmpty(assertStatus)) {
            assertStatus = ImmutableMap.of(EQUALS, SUCCESS);
        }
        return assertStatus;
    }

    public static class FailedAssertion {
        String target;
        String assertion;
        String expected;

        public FailedAssertion(String target, String assertion, String expected) {
            this.target = target;
            this.assertion = assertion;
            this.expected = expected;
        }
        public String description() {
            return Joiner.on(' ').join(target, assertion, expected);
        }
    }

    /**
     * A convenience to collect and validate any assertion failures.
     */
    public static class AssertionSupport {
        private List<FailedAssertion> failures = new ArrayList<>();

        public void fail(String target, String assertion, Object expected) {
            failures.add(new FailedAssertion(target, assertion, expected.toString()));
        }

        /**
         * @throws AssertionError if any failures were collected.
         */
        public void validate() {
            if (0 < failures.size()) {
                StringBuilder summary = new StringBuilder();
                summary.append("Assertion Failures: \n");
                for (FailedAssertion fail : failures) {
                    summary.append(fail.description()).append("\n");
                }
                Asserts.fail(summary.toString());
            }
        }
    }
}
