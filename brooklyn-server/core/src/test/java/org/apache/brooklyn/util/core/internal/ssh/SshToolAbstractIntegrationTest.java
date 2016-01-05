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
package org.apache.brooklyn.util.core.internal.ssh;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

/**
 * Test the operation of the {@link SshTool} utility class; to be extended to test concrete implementations.
 * <p>
 * The test <strong>assumes that</strong> you have configured keys for accessing localhost with the following keys:
 * <ul>
 * <li>passwordless ~/.ssh/id_rsa</li>
 * <li>~/.ssh/id_rsa_with_passphrase with password mypassphrase </li>
 * </ul>
 *
 * <br>
 * Paths for the keys above can override the from Java System Properties.
 * Here are the system properties you can override:
 *
 * <ul>
 * <li>brooklyn.sshPrivateKeyWithPassphrase by default it is ~/.ssh/id_rsa_with_passphrase<br>
 * <li>brooklyn.sshPrivateKeyPassphrase     by default it is mypassphrase<br>
 * <li>brooklyn.sshDefaultPrivateKeyFile    by default it is ~/.ssh/id_rsa<br>
 * </ul>
 *
 * Note that {@link #testSshKeyWithNoKeyDefaultsToIdrsa} requires a passwordless ~/.ssh/id_rsa
 * </p>
 */
public abstract class SshToolAbstractIntegrationTest extends ShellToolAbstractTest {

    private static final Logger log = LoggerFactory.getLogger(SshToolAbstractIntegrationTest.class);
    
    // FIXME need tests which take properties set in entities and brooklyn.properties;
    // but not in this class because it is lower level than entities, Aled would argue.

    // TODO No tests for retry logic and exception handing yet

    public static final String SSH_KEY_WITH_PASSPHRASE = System.getProperty("brooklyn.sshPrivateKeyWithPassphrase", "~/.ssh/id_rsa_with_passphrase");
    public static final String SSH_PASSPHRASE = System.getProperty("brooklyn.sshPrivateKeyPassphrase", "mypassphrase");
    public static final String SSH_DEFAULT_KEYFILE = System.getProperty("brooklyn.sshDefaultPrivateKeyFile", "~/.ssh/id_rsa");

    protected String remoteFilePath;

    protected SshTool tool() { return (SshTool)tool; }
    
    protected abstract SshTool newUnregisteredTool(Map<String,?> flags);

    @Override
    protected SshTool newTool() {
        return newTool(ImmutableMap.of("host", "localhost", "privateKeyFile", SSH_DEFAULT_KEYFILE));
    }
    
    @Override
    protected SshTool newTool(Map<String,?> flags) {
        return (SshTool) super.newTool(flags);
    }
    

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        remoteFilePath = "/tmp/ssh-test-remote-"+Identifiers.makeRandomId(8);
        filesCreated.add(remoteFilePath);
    }
    
    protected void assertRemoteFileContents(String remotePath, String expectedContents) {
        String catout = execCommands("cat "+remotePath);
        assertEquals(catout, expectedContents);
    }
    
    /**
     * @param remotePath
     * @param expectedPermissions Of the form, for example, "-rw-r--r--"
     */
    protected void assertRemoteFilePermissions(String remotePath, String expectedPermissions) {
        String lsout = execCommands("ls -l "+remotePath);
        assertTrue(lsout.contains(expectedPermissions), lsout);
    }
    
    protected void assertRemoteFileLastModifiedIsNow(String remotePath) {
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

    @Test(groups = {"Integration"})
    public void testCopyToServerFromBytes() throws Exception {
        String contents = "echo hello world!\n";
        byte[] contentBytes = contents.getBytes();
        tool().copyToServer(MutableMap.<String,Object>of(), contentBytes, remoteFilePath);

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
        tool().copyToServer(MutableMap.<String,Object>of(), contentsStream, remoteFilePath);

        assertRemoteFileContents(remoteFilePath, contents);
    }

    @Test(groups = {"Integration"})
    public void testCopyToServerWithPermissions() throws Exception {
        tool().copyToServer(ImmutableMap.of("permissions","0754"), "echo hello world!\n".getBytes(), remoteFilePath);

        assertRemoteFilePermissions(remoteFilePath, "-rwxr-xr--");
    }
    
    @Test(groups = {"Integration"})
    public void testCopyToServerWithLastModifiedDate() throws Exception {
        long lastModificationTime = 1234567;
        tool().copyToServer(ImmutableMap.of("lastModificationDate", lastModificationTime), "echo hello world!\n".getBytes(), remoteFilePath);

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
        tool().copyToServer(ImmutableMap.of("permissions", "0754"), new File(localFilePath), remoteFilePath);

        assertRemoteFileContents(remoteFilePath, contents);

        String lsout = execCommands("ls -l "+remoteFilePath);
        assertTrue(lsout.contains("-rwxr-xr--"), lsout);
    }

    @Test(groups = {"Integration"})
    public void testCopyFromServer() throws Exception {
        String contentsWithoutLineBreak = "echo hello world!";
        String contents = contentsWithoutLineBreak+"\n";
        tool().copyToServer(MutableMap.<String,Object>of(), contents.getBytes(), remoteFilePath);
        
        tool().copyFromServer(MutableMap.<String,Object>of(), remoteFilePath, new File(localFilePath));

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
        
        tool().copyToServer(MutableMap.<String,Object>of(), contents.getBytes(), remoteFileInDirPath);

        assertRemoteFileContents(remoteFileInDirPath, contents);
    }
    

    @Test(groups = {"Integration"})
    public void testAllocatePty() {
        final ShellTool localtool = newTool(MutableMap.of("host", "localhost", SshTool.PROP_ALLOCATE_PTY.getName(), true));
        connect(localtool);
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
        final SshTool localtool = newTool(ImmutableMap.<String,Object>builder()
                .put(SshTool.PROP_HOST.getName(), "localhost")
                .put(SshTool.PROP_PRIVATE_KEY_FILE.getName(), SSH_KEY_WITH_PASSPHRASE)
                .put(SshTool.PROP_PRIVATE_KEY_PASSPHRASE.getName(), SSH_PASSPHRASE)
                .build());
        localtool.connect();
        
        assertEquals(tool.execScript(MutableMap.<String,Object>of(), ImmutableList.of("date")), 0);

        // Also needs the negative test to prove that we're really using an ssh-key with a passphrase
        try {
            final SshTool localtool2 = newTool(ImmutableMap.<String,Object>builder()
                    .put(SshTool.PROP_HOST.getName(), "localhost")
                    .put(SshTool.PROP_PRIVATE_KEY_FILE.getName(), SSH_KEY_WITH_PASSPHRASE)
                    .build());
            localtool2.connect();
            fail();
        } catch (Exception e) {
            SshException se = Exceptions.getFirstThrowableOfType(e, SshException.class);
            if (se == null) throw e;
        }
    }

    @Test(groups = {"Integration"})
    public void testSshKeyWithNoKeyDefaultsToIdrsa() throws Exception {
        final SshTool localtool = newTool(ImmutableMap.<String,Object>builder()
                .put(SshTool.PROP_HOST.getName(), "localhost")
                .build());
        tools.add(localtool);
        localtool.connect();
        assertEquals(localtool.execScript(MutableMap.<String,Object>of(), ImmutableList.of("date")), 0);
    }

    @Test(groups = {"Integration"})
    public void testSshKeyWithPrivateKeyData() throws Exception {
        final SshTool localtool = newTool(ImmutableMap.<String,Object>builder()
                .put(SshTool.PROP_HOST.getName(), "localhost")
                .put(SshTool.PROP_PRIVATE_KEY_DATA.getName(), new String(Files.toByteArray(new File(Os.tidyPath(SSH_DEFAULT_KEYFILE))), StandardCharsets.UTF_8))
                .build());
        localtool.connect();

        assertEquals(localtool.execScript(MutableMap.<String,Object>of(), ImmutableList.of("date")), 0);

        // Also needs the negative test to prove that we're really using an ssh-key with a passphrase
        try {
            final SshTool localtool2 = newTool(ImmutableMap.<String,Object>builder()
                    .put(SshTool.PROP_HOST.getName(), "localhost")
                    .put(SshTool.PROP_PRIVATE_KEY_DATA.getName(), "invalid data")
                    .build());
            localtool2.connect();
            localtool2.execScript(MutableMap.<String,Object>of(), ImmutableList.of("date"));
            // Notice that executing a command may succeed for SshCliToolIntegrationTest.testSshKeyWithPrivateKeyData if you already have valid keys loaded in the ssh-agent
            fail();
        } catch (Exception e) {
            SshException se = Exceptions.getFirstThrowableOfType(e, SshException.class);
            if (se == null) throw e;
        }
    }

    @Test(groups = {"Integration"})
    public void testConnectWithInvalidUserThrowsException() throws Exception {
        final ShellTool localtool = newTool(ImmutableMap.of("user", "wronguser", "host", "localhost", "privateKeyFile", SSH_DEFAULT_KEYFILE));
        tools.add(localtool);
        try {
            connect(localtool);
            fail();
        } catch (SshException e) {
            if (!e.toString().contains("failed to connect")) throw e;
        }
    }

    @Test(groups = {"Integration"})
    public void testOutputAsExpected() throws Exception {
        final String CONTENTS = "hello world\n"
            + "bye bye\n";
        execCommands("cat > "+Os.mergePaths(Os.tmp(), "test1")+" << X\n"
            + CONTENTS
            + "X\n");
        String read = execCommands("echo START_FOO", "cat "+Os.mergePaths(Os.tmp(), "test1"), "echo END_FOO");
        log.debug("read back data written, as:\n"+read);
        String contents = Strings.getFragmentBetween(read, "START_FOO", "END_FOO");
        Assert.assertEquals(CONTENTS.trim(), contents.trim());
    }

    @Test(groups = {"Integration"})
    public void testScriptDirPropertiesIsRespected() {
        // For explanation of (some of) the magic behind this command, see http://stackoverflow.com/a/229606/68898
        final String command = "if [[ \"$0\" == \"/var/tmp/\"* ]]; then true; else false; fi";

        SshTool sshTool = newTool(ImmutableMap.<String, Object>builder()
                .put(SshTool.PROP_HOST.getName(), "localhost")
                .build());
        int rc = sshTool.execScript(ImmutableMap.<String, Object>builder()
                .put(SshTool.PROP_SCRIPT_DIR.getName(), "/var/tmp")
                .build(), ImmutableList.of(command));
        assertEquals(rc, 0);
    }
}
