package brooklyn.util.internal.ssh;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Identifiers;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Test the operation of the {@link SshTool} utility class; to be extended to test concrete implementations.
 * 
 * Requires keys set up, e.g. running:
 * 
 * <pre>
 * cd ~/.ssh
 * ssh-keygen
 * id_rsa_with_passphrase
 * mypassphrase
 * mypassphrase
 * </pre>
 * 
 */
public abstract class SshToolIntegrationTest {

    // FIXME need tests which take properties set in entities and brooklyn.properties;
    // but not in this class because it is lower level than entities, Aled would argue.

    // TODO No tests for retry logic and exception handing yet

    public static final String SSH_KEY_WITH_PASSPHRASE = System.getProperty("sshPrivateKeyWithPassphrase", "~/.ssh/id_rsa_with_passphrase");
    public static final String SSH_PASSPHRASE = System.getProperty("sshPrivateKeyPassphrase", "mypassphrase");
    
    protected List<SshTool> tools = Lists.newArrayList();
    protected SshTool tool;
    protected List<String> filesCreated;
    protected String localFilePath;
    protected String remoteFilePath;
    
    protected abstract SshTool newSshTool(Map<String,?> flags);

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        localFilePath = "/tmp/ssh-test-local-"+Identifiers.makeRandomId(8);
        remoteFilePath = "/tmp/ssh-test-remote-"+Identifiers.makeRandomId(8);
        filesCreated = new ArrayList<String>();
        filesCreated.add(localFilePath);
        filesCreated.add(remoteFilePath);

        tool = newSshTool(ImmutableMap.of("host", "localhost", "privateKeyFile", "~/.ssh/id_rsa"));
        tools.add(tool);
        tool.connect();
    }
    
    @AfterMethod(alwaysRun=true)
    public void afterMethod() throws Exception {
        for (SshTool t : tools) {
            t.disconnect();
        }
        for (String fileCreated : filesCreated) {
            new File(fileCreated).delete();
        }
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
        String out = execScript("sleep 5", "exit 0");
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
        String out = execScript("export MYPROP="+bigstring, "echo val is $MYPROP");
        //String out = execScript([ "sleep 5", "export MYPROP="+bigstring, "echo val is \$MYPROP" ])
        assertTrue(out.contains("val is "+bigstring), "out="+out);
    }

    @Test(groups = {"WIP", "Integration"})
    public void testExecScriptBigConcurrentCommand() throws Exception {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();
        try {
            for (int i = 0; i < 10; i++) {
                final SshTool localtool = newSshTool(ImmutableMap.of("host", "localhost", "privateKeyFile", "~/.ssh/id_rsa"));
                tools.add(localtool);
                localtool.connect();
                
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
                final SshTool localtool = newSshTool(ImmutableMap.of("host", "localhost", "privateKeyFile", "~/.ssh/id_rsa"));
                tools.add(localtool);
                localtool.connect();
                
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
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();
        try {
            for (int i = 0; i < 10; i++) {
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

    @Test(groups = {"Integration"})
    public void testCopyToServerFromBytes() throws Exception {
        String contents = "echo hello world!\n";
        byte[] contentBytes = contents.getBytes();
        tool.copyToServer(MutableMap.<String,Object>of(), contentBytes, remoteFilePath);

        assertRemoteFileContents(remoteFilePath, contents);
        assertRemoteFilePermissions(remoteFilePath, "-rw-r--r--");
        
        // TODO would like to also assert lastModified time, but on jenkins the jvm locale
        // and the OS locale are different (i.e. different timezones) so the file time-stamp 
        // is several hours out.
        //assertRemoteFileLastModifiedIsNow(remoteFilePath);
    }

    @Test(groups = {"Integration"})
    public void testCopyToServerFromInputStream() throws Exception {
        String contents = "echo hello world!\n";
        ByteArrayInputStream contentsStream = new ByteArrayInputStream(contents.getBytes());
        tool.copyToServer(MutableMap.<String,Object>of(), contentsStream, remoteFilePath);

        assertRemoteFileContents(remoteFilePath, contents);
    }

    @Test(groups = {"Integration"})
    public void testCopyToServerWithPermissions() throws Exception {
        tool.copyToServer(ImmutableMap.of("permissions","0754"), "echo hello world!\n".getBytes(), remoteFilePath);

        assertRemoteFilePermissions(remoteFilePath, "-rwxr-xr--");
    }
    
    @Test(groups = {"Integration"})
    public void testCopyToServerWithLastModifiedDate() throws Exception {
        long lastModificationTime = 1234567;
        Date lastModifiedDate = new Date(lastModificationTime);
        tool.copyToServer(ImmutableMap.of("lastModificationDate", lastModificationTime), "echo hello world!\n".getBytes(), remoteFilePath);

        String lsout = execCommands("ls -l "+remoteFilePath);//+" | awk '{print \$6 \" \" \$7 \" \" \$8}'"])
        //execCommands([ "ls -l "+remoteFilePath+" | awk '{print \$6 \" \" \$7 \" \" \$8}'"])
        //varies depending on timezone
        assertTrue(lsout.contains("Jan 15  1970") || lsout.contains("Jan 14  1970") || lsout.contains("Jan 16  1970"), lsout);
        //assertLastModified(lsout, lastModifiedDate)
    }
    
    @Test(groups = {"Integration"})
    public void testCopyFileToServerWithPermissions() throws Exception {
        String contents = "echo hello world!\n";
        Files.write(contents, new File(localFilePath), Charsets.UTF_8);
        tool.copyToServer(ImmutableMap.of("permissions", "0754"), new File(localFilePath), remoteFilePath);

        assertRemoteFileContents(remoteFilePath, contents);

        String lsout = execCommands("ls -l "+remoteFilePath);
        assertTrue(lsout.contains("-rwxr-xr--"), lsout);
    }

    @Test(groups = {"Integration"})
    public void testCopyFromServer() throws Exception {
        String contentsWithoutLineBreak = "echo hello world!";
        String contents = contentsWithoutLineBreak+"\n";
        tool.copyToServer(MutableMap.<String,Object>of(), contents.getBytes(), remoteFilePath);
        
        tool.copyFromServer(MutableMap.<String,Object>of(), remoteFilePath, new File(localFilePath));

        List<String> actual = Files.readLines(new File(localFilePath), Charsets.UTF_8);
        assertEquals(actual, ImmutableList.of(contentsWithoutLineBreak));
    }
    
    // TODO No config options in sshj or scp for auto-creating the parent directories
    @Test(enabled=false, groups = {"Integration"})
    public void testCopyFileToNonExistantDir() throws Exception {
        String contents = "echo hello world!\n";
        String remoteFileDirPath = "/tmp/ssh-test-remote-dir-"+Identifiers.makeRandomId(8);
        String remoteFileInDirPath = remoteFileDirPath + File.separator + "ssh-test-remote-"+Identifiers.makeRandomId(8);
        filesCreated.add(remoteFileInDirPath);
        filesCreated.add(remoteFileDirPath);
        
        tool.copyToServer(MutableMap.<String,Object>of(), contents.getBytes(), remoteFileInDirPath);

        assertRemoteFileContents(remoteFileInDirPath, contents);
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
    public void testConnectWithInvalidUserThrowsException() throws Exception {
        final SshTool localtool = newSshTool(ImmutableMap.of("user", "wronguser", "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa"));
        tools.add(localtool);
        try {
            localtool.connect();
            fail();
        } catch (SshException e) {
            if (!e.toString().contains("failed to connect")) throw e;
        }
    }

    @Test(groups = {"Integration"})
    public void testScriptHeader() {
        final SshTool localtool = newSshTool(MutableMap.of("host", "localhost"));
        tools.add(localtool);
        String out = execScript(MutableMap.of("scriptHeader", "#!/bin/bash -e\necho hello world\n"), 
        		localtool, Arrays.asList("echo goodbye world"), null);
        assertTrue(out.contains("goodbye world"), "no goodbye in output: "+out);
        assertTrue(out.contains("hello world"), "no hello in output: "+out);
    }

    @Test(groups = {"Integration"})
    public void testStdErr() {
        final SshTool localtool = newSshTool(MutableMap.of("host", "localhost"));
        tools.add(localtool);
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
    public void testAllocatePty() {
        final SshTool localtool = newSshTool(MutableMap.of("host", "localhost", SshTool.PROP_ALLOCATE_PTY.getName(), true));
        tools.add(localtool);
        Map<String,Object> props = new LinkedHashMap<String, Object>();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        props.put("out", out);
        props.put("err", err);
        int exitcode = localtool.execScript(props, Arrays.asList("echo hello err > /dev/stderr"), null);
        assertTrue(out.toString().contains("hello err"), "no hello in output: "+out+" (err is '"+err+"')");
        assertFalse(err.toString().contains("hello err"), "hello found in stderr: "+err);
        assertEquals(0, exitcode);
    }

    // Requires setting up an extra ssh key, with a passphrase, and adding it to ~/.ssh/authorized_keys
    @Test(groups = {"Integration"})
    public void testSshKeyWithPassphrase() throws Exception {
        final SshTool localtool = newSshTool(ImmutableMap.<String,Object>builder()
                .put(SshTool.PROP_HOST.getName(), "localhost")
                .put(SshTool.PROP_PRIVATE_KEY_FILE.getName(), SSH_KEY_WITH_PASSPHRASE)
                .put(SshTool.PROP_PRIVATE_KEY_PASSPHRASE.getName(), SSH_PASSPHRASE)
                .build());
        tools.add(localtool);
        localtool.connect();
        
        assertEquals(tool.execScript(MutableMap.<String,Object>of(), ImmutableList.of("date")), 0);

        // Also needs the negative test to prove that we're really using an ssh-key with a passphrase
        try {
            final SshTool localtool2 = newSshTool(ImmutableMap.<String,Object>builder()
                    .put(SshTool.PROP_HOST.getName(), "localhost")
                    .put(SshTool.PROP_PRIVATE_KEY_FILE.getName(), SSH_KEY_WITH_PASSPHRASE)
                    .build());
            tools.add(localtool2);
            localtool2.connect();
            fail();
        } catch (Exception e) {
            SshException se = Exceptions.getFirstThrowableOfType(e, SshException.class);
            if (se == null) throw e;
        }
    }
    
    private void assertRemoteFileContents(String remotePath, String expectedContents) {
        String catout = execCommands("cat "+remotePath);
        assertEquals(catout, expectedContents);
    }
    
    /**
     * @param remotePath
     * @param expectedPermissions Of the form, for example, "-rw-r--r--"
     */
    private void assertRemoteFilePermissions(String remotePath, String expectedPermissions) {
        String lsout = execCommands("ls -l "+remotePath);
        assertTrue(lsout.contains(expectedPermissions), lsout);
    }
    
    private void assertRemoteFileLastModifiedIsNow(String remotePath) {
        // Check default last-modified time is `now`.
        // Be lenient in assertion, in case unlucky that clock ticked over to next hour/minute as test was running.
        // TODO Code could be greatly improved, but low priority!
        // Output format:
        //   -rw-r--r--  1   aled  wheel  18  Apr 24  15:03 /tmp/ssh-test-remote-CvFN9zQA
        //   [0]         [1] [2]   [3]    [4] [5] [6] [7]   [8]
        
        String lsout = execCommands("ls -l "+remotePath);
        
        String[] lsparts = lsout.split("\\s+");
        int day = Integer.parseInt(lsparts[6]);
        int hour = Integer.parseInt(lsparts[7].split(":")[0]);
        int minute = Integer.parseInt(lsparts[7].split(":")[1]);
        
        Calendar expected = Calendar.getInstance();
        int expectedDay = expected.get(Calendar.DAY_OF_MONTH);
        int expectedHour = expected.get(Calendar.HOUR_OF_DAY);
        int expectedMinute = expected.get(Calendar.MINUTE);
        
        assertEquals(day, expectedDay, "ls="+lsout+"; lsparts="+Arrays.toString(lsparts)+"; expected="+expected+"; expectedDay="+expectedDay+"; day="+day+"; zone="+expected.getTimeZone());
        assertTrue(Math.abs(hour - expectedHour) <= 1, "ls="+lsout+"; lsparts="+Arrays.toString(lsparts)+"; expected="+expected+"; expectedHour="+expectedHour+"; hour="+hour+"; zone="+expected.getTimeZone());
        assertTrue(Math.abs(minute - expectedMinute) <= 1, "ls="+lsout+"; lsparts="+Arrays.toString(lsparts)+"; expected="+expected+"; expectedMinute="+expectedMinute+"; minute="+minute+"; zone="+expected.getTimeZone());
    }

    private String execCommands(String... cmds) {
        return execCommands(Arrays.asList(cmds));
    }
    
    private String execCommands(List<String> cmds) {
        return execCommands(cmds, ImmutableMap.<String,Object>of());
    }

    private String execCommands(List<String> cmds, Map<String,?> env) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tool.execCommands(ImmutableMap.of("out", out), cmds, env);
        return new String(out.toByteArray());
    }

    private String execScript(String... cmds) {
        return execScript(tool, Arrays.asList(cmds));
    }

    private String execScript(SshTool t, List<String> cmds) {
        return execScript(ImmutableMap.<String,Object>of(), t, cmds, ImmutableMap.<String,Object>of());
    }

    private String execScript(List<String> cmds) {
        return execScript(cmds, ImmutableMap.<String,Object>of());
    }
    
    private String execScript(List<String> cmds, Map<String,?> env) {
        return execScript(MutableMap.<String,Object>of(), tool, cmds, env);
    }
    private String execScript(Map<String, ?> props, SshTool tool, List<String> cmds, Map<String,?> env) {
        Map<String, Object> props2 = new LinkedHashMap<String, Object>(props);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props2.put("out", out);
        int exitcode = tool.execScript(props2, cmds, env);
        String outstr = new String(out.toByteArray());
        assertEquals(exitcode, 0, outstr);
        return outstr;
    }
    
}
