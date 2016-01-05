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
package org.apache.brooklyn.util.core.internal.ssh.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.SshException;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.internal.ssh.SshToolAbstractIntegrationTest;
import org.apache.brooklyn.util.core.internal.ssh.cli.SshCliTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link SshJschTool} utility class.
 */
public class SshCliToolIntegrationTest extends SshToolAbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SshCliToolIntegrationTest.class);
    
    protected SshTool newUnregisteredTool(Map<String,?> flags) {
        return new SshCliTool(flags);
    }

    @Test(groups = {"Integration"})
    public void testFlags() throws Exception {
        final SshTool localtool = newTool(ImmutableMap.of("sshFlags", "-vvv -tt", "host", "localhost"));
        tools.add(localtool);
        try {
            localtool.connect();
            Map<String,Object> props = new LinkedHashMap<String, Object>();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            props.put("out", out);
            props.put("err", err);
            int exitcode = localtool.execScript(props, Arrays.asList("echo hello err > /dev/stderr"), null);
            Assert.assertEquals(0, exitcode, "exitCode="+exitcode+", but expected 0");
            log.debug("OUT from ssh -vvv command is: "+out);
            log.debug("ERR from ssh -vvv command is: "+err);
            assertFalse(err.toString().contains("hello err"), "hello found where it shouldn't have been, in stderr (should have been tty merged to stdout): "+err);
            assertTrue(out.toString().contains("hello err"), "no hello in stdout: "+err);
            // look for word 'ssh' to confirm we got verbose output
            assertTrue(err.toString().toLowerCase().contains("ssh"), "no mention of ssh in stderr: "+err);
        } catch (SshException e) {
            if (!e.toString().contains("failed to connect")) throw e;
        }
    }

    // Need to have at least one test method here (rather than just inherited) for eclipse to recognize it
    @Test(enabled = false)
    public void testDummy() throws Exception {
    }
    
    // TODO When running mvn on the command line (for Aled), this test hangs when prompting for a password (but works in the IDE!)
    // Doing .connect() isn't enough; need to cause ssh or scp to be invoked
    @Test(enabled=false, groups = {"Integration"})
    public void testConnectWithInvalidUserThrowsException() throws Exception {
        final SshTool localtool = newTool(ImmutableMap.of("user", "wronguser", "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa"));
        tools.add(localtool);
        try {
            localtool.connect();
            int result = localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("date"));
            fail("exitCode="+result+", but expected exception");
        } catch (SshException e) {
            if (!e.toString().contains("failed to connect")) throw e;
        }
    }
    
    // TODO ssh-cli doesn't support pass-phrases yet
    @Test(enabled=false, groups = {"Integration"})
    public void testSshKeyWithPassphrase() throws Exception {
        super.testSshKeyWithPassphrase();
    }

    // Setting last modified date not yet supported for cli-based ssh
    @Override
    @Test(enabled=false, groups = {"Integration"})
    public void testCopyToServerWithLastModifiedDate() throws Exception {
        super.testCopyToServerWithLastModifiedDate();
    }
    
    @Test(groups = {"Integration"})
    public void testExecReturningNonZeroExitCode() throws Exception {
        int exitcode = tool.execCommands(MutableMap.<String,Object>of(), ImmutableList.of("exit 123"));
        assertEquals(exitcode, 123);
    }

}
