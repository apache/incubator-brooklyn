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
package brooklyn.test;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.SkipException;

public class PlatformTestSelectorListener implements IInvokedMethodListener {
    private static final String GROUP_UNIX = "UNIX";
    private static final String GROUP_WINDOWS = "Windows";

    public static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        boolean isUnixTest = false;
        boolean isWinTest = false;
        
        String[] groups = method.getTestMethod().getGroups();
        for (String group : groups) {
            isUnixTest = isUnixTest || group.equalsIgnoreCase(GROUP_UNIX);
            isWinTest = isWinTest || group.equalsIgnoreCase(GROUP_WINDOWS);
        }
        
        boolean isWinPlatform = isWindows();
        if (isUnixTest || isWinTest) {
            if (isWinPlatform && isUnixTest && !isWinTest) {
                throw new SkipException("Skipping unix-specific test."); 
            } else if (!isWinPlatform && isWinTest && !isUnixTest) {
                throw new SkipException("Skipping windows-specific test."); 
            }
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {}
}
