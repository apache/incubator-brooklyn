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
package brooklyn.util.internal.ssh.sshj;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.schmizz.sshj.connection.channel.direct.Session;

import org.testng.annotations.Test;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.SshToolAbstractIntegrationTest;
import brooklyn.util.os.Os;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link SshJschTool} utility class.
 */
public class SshjToolIntegrationTest extends SshToolAbstractIntegrationTest {

    @Override
    protected SshTool newUnregisteredTool(Map<String,?> flags) {
        return new SshjTool(flags);
    }

    // TODO requires vt100 terminal emulation to work?
    @Test(enabled = false, groups = {"Integration"})
    public void testExecShellWithCommandTakingStdin() throws Exception {
        // Uses `tee` to redirect stdin to the given file; cntr-d (i.e. char 4) stops tee with exit code 0
        String content = "blah blah";
        String out = execShellDirectWithTerminalEmulation("tee "+remoteFilePath, content, ""+(char)4, "echo file contents: `cat "+remoteFilePath+"`");

        assertTrue(out.contains("file contents: blah blah"), "out="+out);
    }

    @Test(groups = {"Integration"})
    public void testGivesUpAfterMaxRetries() throws Exception {
        final AtomicInteger callCount = new AtomicInteger();
        
        final SshTool localtool = new SshjTool(ImmutableMap.of("sshTries", 3, "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa")) {
            protected SshAction<Session> newSessionAction() {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulating ssh execution failure");
            }
        };
        
        tools.add(localtool);
        try {
            localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("true"));
            fail();
        } catch (SshException e) {
            if (!e.toString().contains("out of retries")) throw e;
            assertEquals(callCount.get(), 3);
        }
    }

    @Test(groups = {"Integration"})
    public void testReturnsOnSuccessWhenRetrying() throws Exception {
        final AtomicInteger callCount = new AtomicInteger();
        final int successOnAttempt = 2;
        final SshTool localtool = new SshjTool(ImmutableMap.of("sshTries", 3, "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa")) {
            protected SshAction<Session> newSessionAction() {
                callCount.incrementAndGet();
                if (callCount.incrementAndGet() >= successOnAttempt) {
                    return super.newSessionAction();
                } else {
                    throw new RuntimeException("Simulating ssh execution failure");
                }
            }
        };
        
        tools.add(localtool);
        localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("true"));
        assertEquals(callCount.get(), successOnAttempt);
    }

    @Test(groups = {"Integration"})
    public void testGivesUpAfterMaxTime() throws Exception {
        final AtomicInteger callCount = new AtomicInteger();
        final SshTool localtool = new SshjTool(ImmutableMap.of("sshTriesTimeout", 1000, "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa")) {
            protected SshAction<Session> newSessionAction() {
                callCount.incrementAndGet();
                try {
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
                throw new RuntimeException("Simulating ssh execution failure");
            }
        };
        
        tools.add(localtool);
        try {
            localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("true"));
            fail();
        } catch (SshException e) {
            if (!e.toString().contains("out of time")) throw e;
            assertEquals(callCount.get(), 2);
        }
    }
    
    @Test(groups = {"Integration"})
    public void testUsesCustomLocalTempDir() throws Exception {
        class SshjToolForTest extends SshjTool {
            public SshjToolForTest(Map<String, ?> map) {
                super(map);
            }
            public File getLocalTempDir() {
                return localTempDir;
            }
        };
        
        final SshjToolForTest localtool = new SshjToolForTest(ImmutableMap.<String, Object>of("host", "localhost"));
        assertNotNull(localtool.getLocalTempDir());
        assertEquals(localtool.getLocalTempDir(), new File(Os.tidyPath(SshjTool.PROP_LOCAL_TEMP_DIR.getDefaultValue())));
        
        String customTempDir = Os.tmp();
        final SshjToolForTest localtool2 = new SshjToolForTest(ImmutableMap.of(
                "host", "localhost", 
                SshjTool.PROP_LOCAL_TEMP_DIR.getName(), customTempDir));
        assertEquals(localtool2.getLocalTempDir(), new File(customTempDir));
        
        String customRelativeTempDir = "~/tmp";
        final SshjToolForTest localtool3 = new SshjToolForTest(ImmutableMap.of(
                "host", "localhost", 
                SshjTool.PROP_LOCAL_TEMP_DIR.getName(), customRelativeTempDir));
        assertEquals(localtool3.getLocalTempDir(), new File(Os.tidyPath(customRelativeTempDir)));
    }


    protected String execShellDirect(List<String> cmds) {
        return execShellDirect(cmds, ImmutableMap.<String,Object>of());
    }
    
    protected String execShellDirect(List<String> cmds, Map<String,?> env) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitcode = ((SshjTool)tool).execShellDirect(ImmutableMap.of("out", out), cmds, env);
        String outstr = new String(out.toByteArray());
        assertEquals(exitcode, 0, outstr);
        return outstr;
    }

    private String execShellDirectWithTerminalEmulation(String... cmds) {
        return execShellDirectWithTerminalEmulation(Arrays.asList(cmds));
    }
    
    private String execShellDirectWithTerminalEmulation(List<String> cmds) {
        return execShellDirectWithTerminalEmulation(cmds, ImmutableMap.<String,Object>of());
    }
    
    private String execShellDirectWithTerminalEmulation(List<String> cmds, Map<String,?> env) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitcode = ((SshjTool)tool).execShellDirect(ImmutableMap.of("allocatePTY", true, "out", out), cmds, env);
        String outstr = new String(out.toByteArray());
        assertEquals(exitcode, 0, outstr);
        return outstr;
    }
    
}
