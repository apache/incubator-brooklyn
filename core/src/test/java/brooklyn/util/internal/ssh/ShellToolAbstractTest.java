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
package brooklyn.util.internal.ssh;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Time;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public abstract class ShellToolAbstractTest {

    protected List<ShellTool> tools = Lists.newArrayList();
    protected List<String> filesCreated;
    protected String localFilePath;
    
    protected ShellTool tool;
    
    protected ShellTool newTool() {
        return newTool(MutableMap.<String,Object>of());
    }
    
    protected ShellTool newTool(Map<String,?> flags) {
        ShellTool t = newUnregisteredTool(flags);
        tools.add(t);
        return t;
    }

    protected abstract ShellTool newUnregisteredTool(Map<String,?> flags);
    
    protected ShellTool tool() { return tool; }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        localFilePath = "/tmp/ssh-test-local-"+Identifiers.makeRandomId(8);
        filesCreated = new ArrayList<String>();
        filesCreated.add(localFilePath);

        tool = newTool();
        connect(tool);
    }
    
    @AfterMethod(alwaysRun=true)
    public void afterMethod() throws Exception {
        for (ShellTool t : tools) {
            if (t instanceof SshTool) ((SshTool)t).disconnect();
        }
        for (String fileCreated : filesCreated) {
            new File(fileCreated).delete();
        }
    }

    protected static void connect(ShellTool tool) {
        if (tool instanceof SshTool)
            ((SshTool)tool).connect();
    }

    @Test(groups = {"Integration"})
    public void testExecConsecutiveCommands() throws Exception {
        String out = execScript("echo run1");
        String out2 = execScript("echo run2");
        
        assertTrue(out.contains("run1"), "out="+out);
        assertTrue(out2.contains("run2"), "out="+out);
    }

    @Test(groups = {"Integration"})
    public void testExecScriptChainOfCommands() throws Exception {
        String out = execScript("export MYPROP=abc", "echo val is $MYPROP");

        assertTrue(out.contains("val is abc"), "out="+out);
    }

    @Test(groups = {"Integration"})
    public void testExecScriptReturningNonZeroExitCode() throws Exception {
        int exitcode = tool.execScript(MutableMap.<String,Object>of(), ImmutableList.of("exit 123"));
        assertEquals(exitcode, 123);
    }

    @Test(groups = {"Integration"})
    public void testExecScriptReturningZeroExitCode() throws Exception {
        int exitcode = tool.execScript(MutableMap.<String,Object>of(), ImmutableList.of("date"));
        assertEquals(exitcode, 0);
    }

    @Test(groups = {"Integration"})
    public void testExecScriptCommandWithEnvVariables() throws Exception {
        String out = execScript(ImmutableList.of("echo val is $MYPROP2"), ImmutableMap.of("MYPROP2", "myval"));

        assertTrue(out.contains("val is myval"), "out="+out);
    }

    @Test(groups = {"Integration"})
    public void testScriptDataNotLost() throws Exception {
        String out = execScript("echo `echo foo``echo bar`");

        assertTrue(out.contains("foobar"), "out="+out);
    }

    @Test(groups = {"Integration"})
    public void testExecScriptWithSleepThenExit() throws Exception {
        Stopwatch watch = Stopwatch.createStarted();
        execScript("sleep 1", "exit 0");
        assertTrue(watch.elapsed(TimeUnit.MILLISECONDS) > 900, "only slept "+Time.makeTimeStringRounded(watch));
    }

    // Really just tests that it returns; the command will be echo'ed automatically so this doesn't assert the command will have been executed
    @Test(groups = {"Integration"})
    public void testExecScriptBigCommand() throws Exception {
        String bigstring = Strings.repeat("a", 10000);
        String out = execScript("echo "+bigstring);
        
        assertTrue(out.contains(bigstring), "out="+out);
    }

    @Test(groups = {"Integration"})
    public void testExecScriptBigChainOfCommand() throws Exception {
        String bigstring = Strings.repeat("abcdefghij", 100); // 1KB
        List<String> cmds = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            cmds.add("export MYPROP"+i+"="+bigstring);
            cmds.add("echo val"+i+" is $MYPROP"+i);
        }
        String out = execScript(cmds);
        
        for (int i = 0; i < 10; i++) {
            assertTrue(out.contains("val"+i+" is "+bigstring), "out="+out);
        }
    }

    @Test(groups = {"Integration"})
    public void testExecScriptAbortsOnCommandFailure() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitcode = tool.execScript(ImmutableMap.of("out", out), ImmutableList.of("export MYPROP=myval", "acmdthatdoesnotexist", "echo val is $MYPROP"));
        String outstr = new String(out.toByteArray());

        assertFalse(outstr.contains("val is myval"), "out="+out);
        assertNotEquals(exitcode,  0);
    }
    
    @Test(groups = {"Integration"})
    public void testExecScriptWithSleepThenBigCommand() throws Exception {
        String bigstring = Strings.repeat("abcdefghij", 1000); // 10KB
        String out = execScript("sleep 2", "export MYPROP="+bigstring, "echo val is $MYPROP");
        assertTrue(out.contains("val is "+bigstring), "out="+out);
    }
    
    @Test(groups = {"WIP", "Integration"})
    public void testExecScriptBigConcurrentCommand() throws Exception {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();
        try {
            for (int i = 0; i < 10; i++) {
                final ShellTool localtool = newTool();
                connect(localtool);
                
                futures.add(executor.submit(new Runnable() {
                        public void run() {
                            String bigstring = Strings.repeat("abcdefghij", 1000); // 10KB
                            String out = execScript(localtool, ImmutableList.of("export MYPROP="+bigstring, "echo val is $MYPROP"));
                            assertTrue(out.contains("val is "+bigstring), "outSize="+out.length()+"; out="+out);
                        }}));
            }
            Futures.allAsList(futures).get();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(groups = {"WIP", "Integration"})
    public void testExecScriptBigConcurrentSleepyCommand() throws Exception {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();
        try {
            long starttime = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                final ShellTool localtool = newTool();
                connect(localtool);
                
                futures.add(executor.submit(new Runnable() {
                        public void run() {
                            String bigstring = Strings.repeat("abcdefghij", 1000); // 10KB
                            String out = execScript(localtool, ImmutableList.of("sleep 2", "export MYPROP="+bigstring, "echo val is $MYPROP"));
                            assertTrue(out.contains("val is "+bigstring), "out="+out);
                        }}));
            }
            Futures.allAsList(futures).get();
            long runtime = System.currentTimeMillis() - starttime;
            
            long OVERHEAD = 20*1000;
            assertTrue(runtime < 2000+OVERHEAD, "runtime="+runtime);
            
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(groups = {"Integration"})
    public void testExecChainOfCommands() throws Exception {
        String out = execCommands("MYPROP=abc", "echo val is $MYPROP");

        assertEquals(out, "val is abc\n");
    }

    @Test(groups = {"Integration"})
    public void testExecReturningNonZeroExitCode() throws Exception {
        int exitcode = tool.execCommands(MutableMap.<String,Object>of(), ImmutableList.of("exit 123"));
        assertEquals(exitcode, 123);
    }

    @Test(groups = {"Integration"})
    public void testExecReturningZeroExitCode() throws Exception {
        int exitcode = tool.execCommands(MutableMap.<String,Object>of(), ImmutableList.of("date"));
        assertEquals(exitcode, 0);
    }

    @Test(groups = {"Integration"})
    public void testExecCommandWithEnvVariables() throws Exception {
        String out = execCommands(ImmutableList.of("echo val is $MYPROP2"), ImmutableMap.of("MYPROP2", "myval"));

        assertEquals(out, "val is myval\n");
    }

    @Test(groups = {"Integration"})
    public void testExecBigCommand() throws Exception {
        String bigstring = Strings.repeat("abcdefghij", 1000); // 10KB
        String out = execCommands("echo "+bigstring);

        assertEquals(out, bigstring+"\n", "actualSize="+out.length()+"; expectedSize="+bigstring.length());
    }

    @Test(groups = {"Integration"})
    public void testExecBigConcurrentCommand() throws Exception {
        runExecBigConcurrentCommand(10, 0L);
    }
    
    // TODO Fails I believe due to synchronization model in SshjTool of calling connect/disconnect.
    // Even with a retry-count of 4, it still fails because some commands are calling disconnect
    // while another concurrently executing command expects to be still connected.
    @Test(groups = {"Integration", "WIP"})
    public void testExecBigConcurrentCommandWithStaggeredStart() throws Exception {
        // This test is to vary the concurrency of concurrent actions
        runExecBigConcurrentCommand(50, 100L);
    }
    
    protected void runExecBigConcurrentCommand(int numCommands, long staggeredDelayBeforeStart) throws Exception {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();
        try {
            for (int i = 0; i < numCommands; i++) {
                long delay = (long) (Math.random() * staggeredDelayBeforeStart);
                if (i > 0 && delay >= 0) Time.sleep(delay);
                
                futures.add(executor.submit(new Runnable() {
                        public void run() {
                            String bigstring = Strings.repeat("abcdefghij", 1000); // 10KB
                            String out = execCommands("echo "+bigstring);
                            assertEquals(out, bigstring+"\n", "actualSize="+out.length()+"; expectedSize="+bigstring.length());
                        }}));
            }
            Futures.allAsList(futures).get();
        } finally {
            executor.shutdownNow();
        }
    }

    // fails if terminal enabled
    @Test(groups = {"Integration"})
    @Deprecated // tests deprecated code
    public void testExecScriptCapturesStderr() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String nonExistantCmd = "acmdthatdoesnotexist";
        tool.execScript(ImmutableMap.of("out", out, "err", err), ImmutableList.of(nonExistantCmd));
        assertTrue(new String(err.toByteArray()).contains(nonExistantCmd+": command not found"), "out="+out+"; err="+err);
    }

    // fails if terminal enabled
    @Test(groups = {"Integration"})
    @Deprecated // tests deprecated code
    public void testExecCapturesStderr() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String nonExistantCmd = "acmdthatdoesnotexist";
        tool.execCommands(ImmutableMap.of("out", out, "err", err), ImmutableList.of(nonExistantCmd));
        String errMsg = new String(err.toByteArray());
        assertTrue(errMsg.contains(nonExistantCmd+": command not found\n"), "errMsg="+errMsg+"; out="+out+"; err="+err);
        
    }

    @Test(groups = {"Integration"})
    public void testScriptHeader() {
        final ShellTool localtool = newTool();
        String out = execScript(MutableMap.of("scriptHeader", "#!/bin/bash -e\necho hello world\n"), 
                localtool, Arrays.asList("echo goodbye world"), null);
        assertTrue(out.contains("goodbye world"), "no goodbye in output: "+out);
        assertTrue(out.contains("hello world"), "no hello in output: "+out);
    }

    @Test(groups = {"Integration"})
    public void testStdErr() {
        final ShellTool localtool = newTool();
        Map<String,Object> props = new LinkedHashMap<String, Object>();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        props.put("out", out);
        props.put("err", err);
        int exitcode = localtool.execScript(props, Arrays.asList("echo hello err > /dev/stderr"), null);
        assertFalse(out.toString().contains("hello err"), "hello found where it shouldn't have been, in stdout: "+out);
        assertTrue(err.toString().contains("hello err"), "no hello in stderr: "+err);
        assertEquals(0, exitcode);
    }

    @Test(groups = {"Integration"})
    public void testRunAsRoot() {
        final ShellTool localtool = newTool();
        Map<String,Object> props = new LinkedHashMap<String, Object>();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        props.put("out", out);
        props.put("err", err);
        props.put(SshTool.PROP_RUN_AS_ROOT.getName(), true);
        int exitcode = localtool.execScript(props, Arrays.asList("whoami"), null);
        assertTrue(out.toString().contains("root"), "not running as root; whoami is: "+out+" (err is '"+err+"')");
        assertEquals(0, exitcode);
    }
    
    @Test(groups = {"Integration"})
    public void testExecScriptEchosExecute() throws Exception {
        String out = execScript("date");
        assertTrue(out.toString().contains("Executed"), "Executed did not display: "+out);
    }
    
    @Test(groups = {"Integration"})
    public void testExecScriptEchosDontExecuteWhenToldNoExtraOutput() throws Exception {
        final ShellTool localtool = newTool();
        Map<String,Object> props = new LinkedHashMap<String, Object>();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        props.put("out", out);
        props.put("err", err);
        props.put(SshTool.PROP_NO_EXTRA_OUTPUT.getName(), true);
        int exitcode = localtool.execScript(props, Arrays.asList("echo hello world"), null);
        assertFalse(out.toString().contains("Executed"), "Executed should not have displayed: "+out);
        assertEquals(out.toString().trim(), "hello world");
        assertEquals(0, exitcode);
    }
    
    protected String execCommands(String... cmds) {
        return execCommands(Arrays.asList(cmds));
    }
    
    protected String execCommands(List<String> cmds) {
        return execCommands(cmds, ImmutableMap.<String,Object>of());
    }

    protected String execCommands(List<String> cmds, Map<String,?> env) {
        execCommands(null, cmds, env);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tool.execCommands(ImmutableMap.of("out", out), cmds, env);
        return new String(out.toByteArray());
    }

    protected String execCommands(ConfigBag config, List<String> cmds, Map<String,?> env) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MutableMap<String,Object> flags = MutableMap.<String,Object>of("out", out);
        if (config!=null) flags.add(config.getAllConfig());
        tool.execCommands(flags, cmds, env);
        return new String(out.toByteArray());
    }

    protected String execScript(String... cmds) {
        return execScript(tool, Arrays.asList(cmds));
    }

    protected String execScript(ShellTool t, List<String> cmds) {
        return execScript(ImmutableMap.<String,Object>of(), t, cmds, ImmutableMap.<String,Object>of());
    }

    protected String execScript(List<String> cmds) {
        return execScript(cmds, ImmutableMap.<String,Object>of());
    }
    
    protected String execScript(List<String> cmds, Map<String,?> env) {
        return execScript(MutableMap.<String,Object>of(), tool, cmds, env);
    }
    
    protected String execScript(Map<String, ?> props, ShellTool tool, List<String> cmds, Map<String,?> env) {
        Map<String, Object> props2 = new LinkedHashMap<String, Object>(props);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props2.put("out", out);
        int exitcode = tool.execScript(props2, cmds, env);
        String outstr = new String(out.toByteArray());
        assertEquals(exitcode, 0, outstr);
        return outstr;
    }

}
