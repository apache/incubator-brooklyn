package brooklyn.util.internal.ssh

import static org.testng.Assert.*

import java.io.File
import java.util.Map
import java.util.concurrent.Executors
import java.util.concurrent.Future

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.util.IdGenerator

import com.google.common.base.Charsets
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors

/**
 * Test the operation of the {@link SshJschTool} utility class.
 */
public class SshjToolLiveTest {

    // TODO No tests for retry logic and exception handing yet
    
    private List<SshjTool> tools = []
    private SshjTool tool
    private List<String> filesCreated
    private String localFilePath
    private String remoteFilePath
    
    @BeforeMethod(alwaysRun=true)//(groups = [ "Integration" ])
    public void setUp() throws Exception {
        localFilePath = "/tmp/sshj-test-local-"+IdGenerator.makeRandomId(8)
        remoteFilePath = "/tmp/sshj-test-remote-"+IdGenerator.makeRandomId(8)
        filesCreated = new ArrayList<String>()
        filesCreated.add(localFilePath)
        filesCreated.add(remoteFilePath)

        tool = new SshjTool(host:'localhost', privateKeyFile:"~/.ssh/id_rsa")
        tools.add(tool)
        tool.connect()
    }
    
    @AfterMethod(alwaysRun=true)
    public void afterMethod() throws Exception {
        for (SshjTool t : tools) {
            t.disconnect();
        }
        for (String fileCreated : filesCreated) {
            new File(fileCreated).delete();
        }
    }

    @Test(groups = [ "Integration" ])
    public void testExecConsecutiveCommands() {
        String out = execShell([ "echo run1" ])
        String out2 = execShell([ "echo run2" ])
        
        assertTrue(out.contains("run1"), "out="+out);
        assertTrue(out2.contains("run2"), "out="+out);
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellChainOfCommands() {
        String out = execShell([ "export MYPROP=abc", "echo val is \$MYPROP" ])

        assertTrue(out.contains("val is abc"), "out="+out);
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellReturningNonZeroExitCode() {
        int exitcode = tool.execShell([:], ["exit 123"])
        assertEquals(exitcode, 123)
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellReturningZeroExitCode() {
        int exitcode = tool.execShell([:], ["date"])
        assertEquals(exitcode, 0)
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellCommandWithEnvVariables() {
        String out = execShell([ "echo val is \$MYPROP2" ], [MYPROP2:"myval"])

        assertTrue(out.contains("val is myval"), "out="+out);
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellWithCommandTakingStdin() {
        // Uses `tee` to redirect stdin to the given file; cntr-d (i.e. char 4) stops tee with exit code 0
        String content = "blah blah"
        String out = execShellDirect([ "tee "+remoteFilePath, content, ""+(char)4, "echo file contents: `cat "+remoteFilePath+"`" ])

        assertTrue(out.contains("file contents: blah blah"), "out="+out);
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellWithSleepThenExit() {
        String out = execShell([ "sleep 5", "exit 0" ])
    }

    // Really just tests that it returns; the command will be echo'ed automatically so this doesn't assert the command will have been executed
    @Test(groups = [ "Integration" ])
    public void testExecShellBigCommand() {
        String bigstring = Strings.repeat("a", 10000)
        String out = execShell(["echo "+bigstring])
        
        assertTrue(out.contains(bigstring), "out="+out);
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellBigChainOfCommand() {
        String bigstring = Strings.repeat("abcdefghij", 100) // 1KB
        List<String> cmds = []
        for (int i = 0; i < 10; i++) {
            cmds.add("export MYPROP"+i+"="+bigstring)
            cmds.add("echo val"+i+" is \$MYPROP"+i)
        }
        String out = execShell(cmds)
        
        for (int i = 0; i < 10; i++) {
            assertTrue(out.contains("val"+i+" is "+bigstring), "out="+out);
        }
    }

    @Test(groups = [ "Integration" ])
    public void testExecShellAbortsOnCommandFailure() {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        int exitcode = tool.execShell(out:out, [ "export MYPROP=myval", "acmdthatdoesnotexist", "echo val is \$MYPROP" ])
        String outstr = new String(out.toByteArray())

        assertFalse(outstr.contains("val is myval"), "out="+out);
    }
    
    @Test(groups = [ "Integration" ])
    public void testExecShellWithSleepThenBigCommand() {
        String bigstring = Strings.repeat("abcdefghij", 1000) // 10KB
        String out = execShell([ "export MYPROP="+bigstring, "echo val is \$MYPROP" ])
        //String out = execShell([ "sleep 5", "export MYPROP="+bigstring, "echo val is \$MYPROP" ])
        assertTrue(out.contains("val is "+bigstring), "out="+out);
    }

    @Test(groups = [ "WIP", "Integration" ])
    public void testExecShellBigConcurrentCommand() {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
        List<Future<?>> futures = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 10; i++) {
                final SshjTool localtool = new SshjTool(host:'localhost', privateKeyFile:"~/.ssh/id_rsa")
                tools.add(localtool)
                localtool.connect()
                
                futures.add(executor.submit(new Runnable() {
                        public void run() {
                            String bigstring = Strings.repeat("abcdefghij", 1000) // 10KB
                            String out = execShell(localtool, [ "export MYPROP="+bigstring, "echo val is \$MYPROP" ])
                            assertTrue(out.contains("val is "+bigstring), "out="+out);
                        }}));
            }
            Futures.allAsList(futures).get();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(groups = [ "WIP", "Integration" ])
    public void testExecShellBigConcurrentSleepyCommand() {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
        List<Future<?>> futures = new ArrayList<Future<?>>();
        try {
            long starttime = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                final SshjTool localtool = new SshjTool(host:'localhost', privateKeyFile:"~/.ssh/id_rsa")
                tools.add(localtool)
                localtool.connect()
                
                futures.add(executor.submit(new Runnable() {
                        public void run() {
                            String bigstring = Strings.repeat("abcdefghij", 1000) // 10KB
                            String out = execShell(localtool, [ "sleep 2", "export MYPROP="+bigstring, "echo val is \$MYPROP" ])
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

    @Test(groups = [ "Integration" ])
    public void testExecChainOfCommands() {
        String out = execCommands([ "MYPROP=abc", "echo val is \$MYPROP" ])

        assertEquals(out, "val is abc\n");
    }

    @Test(groups = [ "Integration" ])
    public void testExecReturningNonZeroExitCode() {
        int exitcode = tool.execCommands([:], ["exit 123"])
        assertEquals(exitcode, 123)
    }

    @Test(groups = [ "Integration" ])
    public void testExecReturningZeroExitCode() {
        int exitcode = tool.execCommands([:], ["date"])
        assertEquals(exitcode, 0)
    }

    @Test(groups = [ "Integration" ])
    public void testExecCommandWithEnvVariables() {
        String out = execCommands([ "echo val is \$MYPROP2" ], [MYPROP2:"myval"])

        assertEquals(out, "val is myval\n");
    }

    @Test(groups = [ "Integration" ])
    public void testExecBigCommand() {
        String bigstring = Strings.repeat("abcdefghij", 1000) // 10KB
        String out = execCommands([ "echo "+bigstring ])

        assertEquals(out, bigstring+"\n");
    }

    @Test(groups = [ "Integration" ])
    public void testExecBigConcurrentCommand() {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
        List<Future<?>> futures = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 10; i++) {
                futures.add(executor.submit(new Runnable() {
                        public void run() {
                            String bigstring = Strings.repeat("abcdefghij", 1000) // 10KB
                            String out = execCommands([ "echo "+bigstring ])
                            assertEquals(out, bigstring+"\n");
                        }}));
            }
            Futures.allAsList(futures).get();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(groups = [ "Integration" ])
    public void testCreateFileFromString() {
        String contents = "echo hello world!\n";
        
        tool.createFile([:], remoteFilePath, contents)
        
        assertRemoteFileContents(remoteFilePath, contents)
        assertRemoteFilePermissions(remoteFilePath, "-rw-r--r--")
        assertRemoteFileLastModifiedIsNow(remoteFilePath);
    }

    @Test(groups = [ "Integration" ])
    public void testCreateFileWithPermissions() {
        tool.createFile([permissions:'0754'], remoteFilePath, "echo hello world!\n")

        String out = execCommands([ "ls -l "+remoteFilePath ])
        assertTrue(out.contains("-rwxr-xr--"), out);
    }
    
    @Test(groups = [ "Integration" ])
    public void testCreateFileWithLastModifiedDate() {
        long lastModificationTime = 1234567;
        Date lastModifiedDate = new Date(lastModificationTime)
        tool.createFile([lastModificationDate:lastModificationTime], remoteFilePath, "echo hello world!\n")

        String lsout = execCommands([ "ls -l "+remoteFilePath]);//+" | awk '{print \$6 \" \" \$7 \" \" \$8}'"])
        //execCommands([ "ls -l "+remoteFilePath+" | awk '{print \$6 \" \" \$7 \" \" \$8}'"])
        //varies depending on timezone
        assertTrue(lsout.contains("Jan 15  1970") || lsout.contains("Jan 14  1970") || lsout.contains("Jan 16  1970"), lsout)
        //assertLastModified(lsout, lastModifiedDate)
    }
    
    @Test(groups = [ "Integration" ])
    public void testCopyFileToServerWithPermissions() {
        String contents = "echo hello world!\n"
        Files.write(contents, new File(localFilePath), Charsets.UTF_8)
        tool.copyToServer(permissions:'0754', new File(localFilePath), remoteFilePath)

        assertRemoteFileContents(remoteFilePath, contents)

        String lsout = execCommands([ "ls -l "+remoteFilePath ])
        assertTrue(lsout.contains("-rwxr-xr--"), lsout);
    }

    @Test(groups = [ "Integration" ])
    public void testTransferFileToServer() {
        String contents = "echo hello world!\n"
        ByteArrayInputStream contentsStream = new ByteArrayInputStream(contents.getBytes())
        tool.transferFileTo([:], contentsStream, remoteFilePath)

        assertRemoteFileContents(remoteFilePath, contents)
    }

    @Test(groups = [ "Integration" ])
    public void testCreateFileFromBytes() {
        String contents = "echo hello world!\n"
        byte[] contentBytes = contents.getBytes()
        tool.createFile([:], remoteFilePath, contentBytes)

        assertRemoteFileContents(remoteFilePath, contents)
    }

    @Test(groups = [ "Integration" ])
    public void testCreateFileFromInputStream() {
        String contents = "echo hello world!\n"
        ByteArrayInputStream contentsStream = new ByteArrayInputStream(contents.getBytes())
        tool.createFile([:], remoteFilePath, contentsStream, contents.length())

        assertRemoteFileContents(remoteFilePath, contents)
    }

    @Test(groups = [ "Integration" ])
    public void testTransferFileFromServer() {
        String contentsWithoutLineBreak = "echo hello world!"
        String contents = contentsWithoutLineBreak+"\n"
        tool.createFile([:], remoteFilePath, contents)
        
        tool.transferFileFrom([:], remoteFilePath, localFilePath)

        List<String> actual = Files.readLines(new File(localFilePath), Charsets.UTF_8)
        assertEquals(actual, ImmutableList.of(contentsWithoutLineBreak))
    }
    
    // TODO No config options in sshj or scp for auto-creating the parent directories
    @Test(enabled=false, groups = [ "Integration" ])
    public void testCreateFileInNonExistantDir() {
        String contents = "echo hello world!\n"
        String remoteFileDirPath = "/tmp/sshj-test-remote-dir-"+IdGenerator.makeRandomId(8)
        String remoteFileInDirPath = remoteFileDirPath + File.separator + "sshj-test-remote-"+IdGenerator.makeRandomId(8)
        filesCreated.add(remoteFileInDirPath)
        filesCreated.add(remoteFileDirPath)
        
        tool.createFile([:], remoteFileInDirPath, contents)

        assertRemoteFileContents(remoteFileInDirPath, contents)
    }
    
    // TODO stderr seems to be written to stdout!?
    @Test(enabled=false, groups = [ "Integration" ])
    public void testExecShellCapturesStderr() {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        String nonExistantCmd = "acmdthatdoesnotexist"
        tool.execShell([out:out, err:err], [nonExistantCmd])
        assertEquals(new String(err.toByteArray()), "-bash: $nonExistantCmd: command not found\n", "out="+out+"; err="+err);
    }

    // TODO stderr seems to be written to stdout!?
    @Test(enabled=false, groups = [ "Integration" ])
    public void testExecCapturesStderr() {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        String nonExistantCmd = "acmdthatdoesnotexist"
        tool.execCommands([out:out, err:err], [nonExistantCmd])
        assertEquals(new String(err.toByteArray()), "-bash: $nonExistantCmd: command not found\n", "out="+out+"; err="+err);
        
    }

    private void assertRemoteFileContents(String remotePath, String expectedContents) {
        String catout = execCommands([ "cat "+remotePath ])
        assertEquals(catout, expectedContents);
    }
    
    /**
     * @param remotePath
     * @param expectedPermissions Of the form, for example, "-rw-r--r--"
     */
    private void assertRemoteFilePermissions(String remotePath, String expectedPermissions) {
        String lsout = execCommands([ "ls -l "+remotePath ])
        assertTrue(lsout.contains(expectedPermissions), lsout);
    }
    
    private void assertRemoteFileLastModifiedIsNow(String remotePath) {
        // Check default last-modified time is `now`.
        // Be lenient in assertion, in case unlucky that clock ticked over to next hour/minute as test was running.
        // TODO Code could be greatly improved, but low priority!
        // Output format:
        //   -rw-r--r--  1   aled  wheel  18  Apr 24  15:03 /tmp/sshj-test-remote-CvFN9zQA
        //   [0]         [1] [2]   [3]    [4] [5] [6] [7]   [8]
        
        String lsout = execCommands([ "ls -l "+remotePath ])
        
        String[] lsparts = lsout.split("\\s+");
        int day = Integer.parseInt(lsparts[6])
        int hour = Integer.parseInt(lsparts[7].split(":")[0])
        int minute = Integer.parseInt(lsparts[7].split(":")[1])
        assertEquals(day, Calendar.getInstance().get(Calendar.DAY_OF_MONTH), "ls="+lsout+"; lsparts="+Arrays.toString(lsparts))
        assertTrue(Math.abs(hour - Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) <= 1, "ls="+lsout+"; lsparts="+Arrays.toString(lsparts))
        assertTrue(Math.abs(minute - Calendar.getInstance().get(Calendar.MINUTE)) <= 1, "ls="+lsout+"; lsparts="+Arrays.toString(lsparts))
    }
    
    private String execCommands(List<String> cmds, Map<String,?> env=[:]) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        tool.execCommands(out:out, cmds, env)
        return new String(out.toByteArray())
    }

    private String execShell(List<String> cmds, Map<String,?> env=[:]) {
        return execShell(tool, cmds, env)
    }
    
    private String execShell(SshjTool t, List<String> cmds, Map<String,?> env=[:]) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        int exitcode = t.execShell(out:out, cmds, env)
        String outstr = new String(out.toByteArray())
        assertEquals(exitcode, 0, outstr)
        return outstr
    }
    
    private String execShellDirect(List<String> cmds, Map<String,?> env=[:]) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        int exitcode = tool.execShellDirect(out:out, cmds, env)
        String outstr = new String(out.toByteArray())
        assertEquals(exitcode, 0, outstr)
        return outstr
    }
}
