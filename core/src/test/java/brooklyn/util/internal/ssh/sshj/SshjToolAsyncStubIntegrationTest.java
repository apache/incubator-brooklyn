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
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.brooklyn.core.internal.BrooklynFeatureEnablement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.SshAbstractTool.SshAction;
import brooklyn.util.internal.ssh.sshj.SshjTool.ShellAction;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Tests for async-exec with {@link SshjTool}, where it stubs out the actual ssh commands
 * to return a controlled sequence of responses.
 */
public class SshjToolAsyncStubIntegrationTest {

    static class InjectedResult {
        Predicate<SshjTool.ShellAction> expected;
        Function<SshjTool.ShellAction, Integer> result;
        
        InjectedResult(Predicate<SshjTool.ShellAction> expected, Function<SshjTool.ShellAction, Integer> result) {
            this.expected = expected;
            this.result = result;
        }
    }
    
    private SshjTool tool;
    private List<InjectedResult> sequence;
    int counter = 0;
    private boolean origFeatureEnablement;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        origFeatureEnablement = BrooklynFeatureEnablement.enable(BrooklynFeatureEnablement.FEATURE_SSH_ASYNC_EXEC);
        sequence = Lists.newArrayList();
        counter = 0;
        
        tool = new SshjTool(ImmutableMap.<String,Object>of("host", "localhost")) {
            @SuppressWarnings("unchecked")
            protected <T, C extends SshAction<T>> T acquire(C action, int sshTries, Duration sshTriesTimeout) {
                if (action instanceof SshjTool.ShellAction) {
                    SshjTool.ShellAction shellAction = (SshjTool.ShellAction) action;
                    InjectedResult injectedResult = sequence.get(counter);
                    assertTrue(injectedResult.expected.apply(shellAction), "counter="+counter+"; cmds="+shellAction.commands);
                    counter++;
                    return (T) injectedResult.result.apply(shellAction);
                }
                return super.acquire(action, sshTries, sshTriesTimeout);
            }
        };
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (tool != null) tool.disconnect();
        } finally {
            BrooklynFeatureEnablement.setEnablement(BrooklynFeatureEnablement.FEATURE_SSH_ASYNC_EXEC, origFeatureEnablement);
        }
    }
    
    private Predicate<SshjTool.ShellAction> containsCmd(final String cmd) {
        return new Predicate<SshjTool.ShellAction>() {
            @Override public boolean apply(ShellAction input) {
                return input != null && input.commands.toString().contains(cmd);
            }
        };
    }
    
    private Function<SshjTool.ShellAction, Integer> returning(final int result, final String stdout, final String stderr) {
        return new Function<SshjTool.ShellAction, Integer>() {
            @Override public Integer apply(ShellAction input) {
                try {
                    if (stdout != null && input.out != null) input.out.write(stdout.getBytes());
                    if (stderr != null && input.err != null) input.err.write(stderr.getBytes());
                } catch (IOException e) {
                    throw Exceptions.propagate(e);
                }
                return result;
            }
        };
    }
    
    @Test(groups="Integration")
    public void testPolls() throws Exception {
        sequence = ImmutableList.of(
                new InjectedResult(containsCmd("nohup"), returning(0, "", "")),
                new InjectedResult(containsCmd("# Long poll"), returning(0, "mystringToStdout", "mystringToStderr")));

        runTest(0, "mystringToStdout", "mystringToStderr");
        assertEquals(counter, sequence.size());
    }
    
    @Test(groups="Integration")
    public void testPollsAndReturnsNonZeroExitCode() throws Exception {
        sequence = ImmutableList.of(
                new InjectedResult(containsCmd("nohup"), returning(0, "", "")),
                new InjectedResult(containsCmd("# Long poll"), returning(123, "mystringToStdout", "mystringToStderr")),
                new InjectedResult(containsCmd("# Retrieve status"), returning(0, "123", "")));

        runTest(123, "mystringToStdout", "mystringToStderr");
        assertEquals(counter, sequence.size());
    }
    
    @Test(groups="Integration")
    public void testPollsRepeatedly() throws Exception {
        sequence = ImmutableList.of(
                new InjectedResult(containsCmd("nohup"), returning(0, "", "")),
                new InjectedResult(containsCmd("# Long poll"), returning(125, "mystringToStdout", "mystringToStderr")),
                new InjectedResult(containsCmd("# Retrieve status"), returning(0, "", "")),
                new InjectedResult(containsCmd("# Long poll"), returning(125, "mystringToStdout2", "mystringToStderr2")),
                new InjectedResult(containsCmd("# Retrieve status"), returning(0, "", "")),
                new InjectedResult(containsCmd("# Long poll"), returning(-1, "mystringToStdout3", "mystringToStderr3")),
                new InjectedResult(containsCmd("# Long poll"), returning(125, "mystringToStdout4", "mystringToStderr4")),
                new InjectedResult(containsCmd("# Retrieve status"), returning(0, "", "")),
                new InjectedResult(containsCmd("# Long poll"), returning(0, "mystringToStdout5", "mystringToStderr5")));

        runTest(0,
                "mystringToStdout"+"mystringToStdout2"+"mystringToStdout3"+"mystringToStdout4"+"mystringToStdout5",
                "mystringToStderr"+"mystringToStderr2"+"mystringToStderr3"+"mystringToStderr4"+"mystringToStderr5");
        assertEquals(counter, sequence.size());
    }
    
    protected void runTest(int expectedExit, String expectedStdout, String expectedStderr) throws Exception {
        List<String> cmds = ImmutableList.of("abc");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = tool.execScript(
                ImmutableMap.of(
                        "out", out, 
                        "err", err, 
                        SshjTool.PROP_EXEC_ASYNC.getName(), true, 
                        SshjTool.PROP_NO_EXTRA_OUTPUT.getName(), true,
                        SshjTool.PROP_EXEC_ASYNC_POLLING_TIMEOUT.getName(), Duration.ONE_MILLISECOND), 
                cmds, 
                ImmutableMap.<String,String>of());
        String outStr = new String(out.toByteArray());
        String errStr = new String(err.toByteArray());

        assertEquals(exitCode, expectedExit);
        assertEquals(outStr.trim(), expectedStdout);
        assertEquals(errStr.trim(), expectedStderr);
    }
}
