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
package brooklyn.util.internal.ssh.cli;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.SshToolAbstractPerformanceTest;

/**
 * Test the performance of different variants of invoking the sshj tool.
 * 
 * Intended for human-invocation and inspection, to see which parts are most expensive.
 */
public class SshCliToolPerformanceTest extends SshToolAbstractPerformanceTest {

    @Override
    protected SshTool newSshTool(Map<String,?> flags) {
        return new SshCliTool(flags);
    }
    
    // Need to have at least one test method here (rather than just inherited) for eclipse to recognize it
    @Test(enabled = false)
    public void testDummy() throws Exception {
    }
}
