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
package org.apache.brooklyn.feed.ssh;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class SshValueFunctionsTest {

    private SshPollValue val = new SshPollValue(null, 123, "mystdout", "mystderr");
    
    @Test
    public void testExitCode() throws Exception {
        assertEquals(SshValueFunctions.exitStatus().apply(val), (Integer)123);
    }
    
    @Test
    public void testStdout() throws Exception {
        assertEquals(SshValueFunctions.stdout().apply(val), "mystdout");
    }
    
    @Test
    public void testStderr() throws Exception {
        assertEquals(SshValueFunctions.stderr().apply(val), "mystderr");
    }
}
