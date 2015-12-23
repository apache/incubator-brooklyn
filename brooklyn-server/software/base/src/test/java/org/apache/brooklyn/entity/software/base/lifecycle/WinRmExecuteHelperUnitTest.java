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

import org.apache.brooklyn.entity.software.base.DoNothingWinRmSoftwareProcessDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WinRmExecuteHelperUnitTest {
    private static final String NON_ZERO_CODE_COMMAND = "false";

    @Test
    public void testZeroExitCode() {
        DoNothingWinRmSoftwareProcessDriver nativeWindowsScriptRunner = mock(DoNothingWinRmSoftwareProcessDriver.class);

        WinRmExecuteHelper scriptHelper = new WinRmExecuteHelper(nativeWindowsScriptRunner, "test-zero-code-task");
        Assert.assertEquals(scriptHelper.executeInternal(), 0, "WinRmExecuteHelper doesn't return zero code");
    }

    @Test
    public void testNonZeroExitCode() {
        DoNothingWinRmSoftwareProcessDriver nativeWindowsScriptRunner = mock(DoNothingWinRmSoftwareProcessDriver.class);
        when(nativeWindowsScriptRunner.executeNativeOrPsCommand(any(Map.class), any(String.class), any(String.class), any(String.class), any(Boolean.class))).thenReturn(1);

        WinRmExecuteHelper scriptHelper = new WinRmExecuteHelper(nativeWindowsScriptRunner, "test-zero-code-task")
                .setCommand(NON_ZERO_CODE_COMMAND);
        Assert.assertNotEquals(scriptHelper.executeInternal(), 0, "WinRmExecuteHelper return zero code for non-zero code task");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testNonZeroExitCodeException() {
        DoNothingWinRmSoftwareProcessDriver nativeWindowsScriptRunner = mock(DoNothingWinRmSoftwareProcessDriver.class);
        when(nativeWindowsScriptRunner.executeNativeOrPsCommand(any(Map.class), any(String.class), any(String.class), any(String.class), any(Boolean.class))).thenReturn(1);

        WinRmExecuteHelper scriptHelper = new WinRmExecuteHelper(nativeWindowsScriptRunner, "test-zero-code-task")
                .failOnNonZeroResultCode()
                .setCommand(NON_ZERO_CODE_COMMAND);
        scriptHelper.executeInternal();
    }
}
