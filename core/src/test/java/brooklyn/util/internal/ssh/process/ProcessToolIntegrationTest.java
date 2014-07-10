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
package brooklyn.util.internal.ssh.process;

import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.ShellToolAbstractTest;

/**
 * Test the operation of the {@link ProcessTool} utility class.
 */
public class ProcessToolIntegrationTest extends ShellToolAbstractTest {

    @Override
    protected ProcessTool newUnregisteredTool(Map<String,?> flags) {
        return new ProcessTool(flags);
    }

    // ones here included as *non*-integration tests. must run on windows and linux.
    // (also includes integration tests from parent)

    @Test(groups="UNIX")
    public void testPortableCommand() throws Exception {
        String out = execScript("echo hello world");
        assertTrue(out.contains("hello world"), "out="+out);
    }

    @Test(groups="Integration")
    public void testLoginShell() {
        // this detection scheme only works for commands; can't test whether it works for scripts without 
        // requiring stuff in bash_profile / profile / etc, which gets hard to make portable;
        // it is nearly the same code path on the impl so this is probably enough 
        
        final String LOGIN_SHELL_CHECK = "shopt -q login_shell && echo 'yes, login shell' || echo 'no, not login shell'";
        ConfigBag config = ConfigBag.newInstance().configure(ProcessTool.PROP_NO_EXTRA_OUTPUT, true);
        String out;
        
        out = execCommands(config, Arrays.asList(LOGIN_SHELL_CHECK), null);
        Assert.assertEquals(out.trim(), "no, not login shell", "out = "+out);
        
        config.configure(ProcessTool.PROP_LOGIN_SHELL, true);
        out = execCommands(config, Arrays.asList(LOGIN_SHELL_CHECK), null);
        Assert.assertEquals(out.trim(), "yes, login shell", "out = "+out);
    }
    
}
