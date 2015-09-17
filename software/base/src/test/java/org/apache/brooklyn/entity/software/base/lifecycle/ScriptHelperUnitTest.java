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
package org.apache.brooklyn.entity.software.base.lifecycle;

import org.apache.brooklyn.entity.software.base.DoNothingSoftwareProcessDriver;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.isA;

public class ScriptHelperUnitTest {
    private static final String NON_ZERO_CODE_COMMAND = "false";

    @Test
    public void testZeroExitCode() {
        DoNothingSoftwareProcessDriver sshRunner = createMock(DoNothingSoftwareProcessDriver.class);

        ScriptHelper scriptHelper = new ScriptHelper(sshRunner, "test-zero-code-task");
        Asserts.assertTrue(scriptHelper.executeInternal() == 0, "ScriptHelper doesn't return zero code");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testNonZeroExitCodeException() {
        DoNothingSoftwareProcessDriver sshRunner = createMock(DoNothingSoftwareProcessDriver.class);
        expect(sshRunner.execute(isA(Map.class), isA(List.class), isA(String.class))).andReturn(1);
        replay(sshRunner);

        ScriptHelper scriptHelper = new ScriptHelper(sshRunner, "test-zero-code-task")
                .failOnNonZeroResultCode()
                .body.append(NON_ZERO_CODE_COMMAND);
        Asserts.assertTrue(scriptHelper.executeInternal() != 0, "ScriptHelper return zero code for non-zero code task");
    }
}
