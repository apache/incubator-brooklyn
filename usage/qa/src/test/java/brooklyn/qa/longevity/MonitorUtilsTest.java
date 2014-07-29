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
package brooklyn.qa.longevity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.testng.annotations.Test;

import brooklyn.qa.longevity.MonitorUtils.ProcessHasStderr;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

public class MonitorUtilsTest {

    @Test(enabled=false, timeOut=1000) // Demonstrates that process.waitFor() hangs for big output streams
    public void testExecuteAndWaitFor() throws Exception {
        Process process = createDumpingProcess(false);
        process.waitFor();
        fail("Should block while waiting to consume process output");
    }

    @Test(enabled=false, timeOut=1000) // Demonstrates that process.waitFor() hangs for big err streams
    public void testExecuteAndWaitForErr() throws Exception {
        Process process = createDumpingProcess(true);
        process.waitFor();
        fail("Should block while waiting to consume process output");
    }

    @Test(timeOut=1000)
    public void testExecuteAndWaitForConsumingOutputStream() throws Exception {
        Process process = createDumpingProcess(false);
        String out = MonitorUtils.waitFor(process);
        assertTrue(out.length() > 100000, "out.size="+out.length());
    }

    @Test(timeOut=1000, expectedExceptions=IllegalStateException.class)
    public void testExecuteAndWaitForConsumingErrorStream() throws Exception {
        Process process = createDumpingProcess(true);
        MonitorUtils.waitFor(process);
    }

    private Process createDumpingProcess(boolean writeToErr) throws IOException {
        String errSuffix = writeToErr ? " >&2" : "";
        //Windows limits the length of the arguments so echo multiple times instead
        String bigstr = Strings.repeat("a", 8000);
        String bigcmd = Strings.repeat(getSilentPrefix() + "echo " + bigstr + errSuffix + Os.LINE_SEPARATOR, 15);
        File file = Os.newTempFile("test-consume", ".bat");
        file.setExecutable(true);
        Files.write(bigcmd, file, Charsets.UTF_8);
        Process process = MonitorUtils.exec(file.getAbsolutePath());
        return process;
    }

    @Test(groups="UNIX")
    public void testFindOwnPid() throws Exception {
        int ownpid = MonitorUtils.findOwnPid();
        assertTrue(ownpid > 0, "ownpid=$ownpid");
        assertTrue(MonitorUtils.isPidRunning(ownpid, "java"),"java is not running");
    }

    @Test(groups="UNIX")
    public void testIsPidRunning() throws Exception {
        int usedPid = MonitorUtils.findOwnPid();

        //the child process will terminate freeing it PID
        String[] cmd = new String[]{"bash", "-c", "echo $$"};
        Process process = Runtime.getRuntime().exec(cmd);
        String out = MonitorUtils.waitFor(process);
        int unusedPid = Integer.parseInt(out.trim());

        assertTrue(MonitorUtils.isPidRunning(usedPid));
        assertFalse(MonitorUtils.isPidRunning(unusedPid));
        
        try {
            assertFalse(MonitorUtils.isPidRunning(1234567)); // too large
        } catch (ProcessHasStderr e) {
            // expected on osx
        }
    }

    @Test(groups="UNIX")
    public void testGetRunningPids() throws Exception {
        int ownpid = MonitorUtils.findOwnPid();

        List<Integer> javapids = MonitorUtils.getRunningPids("java");

        assertTrue(javapids.contains(ownpid), "javapids="+javapids+"; ownpid="+ownpid);
    }

    @Test
    public void testIsUrlUp() throws Exception {
        assertFalse(MonitorUtils.isUrlUp(new URL("http://localhost/thispathdoesnotexist")));
    }

    @Test(groups="UNIX")
    public void testSearchLog() throws Exception {
        String fileContents = "line1\nline2\nline3\n";
        File file = File.createTempFile("monitorUtilsTest.testSearchLog", ".txt");
        Files.write(fileContents, file, Charsets.UTF_8);

        try {
            assertEquals(MonitorUtils.searchLog(file, "line1"), Arrays.asList("line1"));
            assertEquals(MonitorUtils.searchLog(file, "line1|line2"), Arrays.asList("line1", "line2"));
            assertEquals(MonitorUtils.searchLog(file, "textnotthere"), Collections.emptyList());
            assertEquals(MonitorUtils.searchLog(file, "line"), Arrays.asList("line1", "line2", "line3"));
        } finally {
            file.delete();
        }
    }

    @Test(groups="Integration")
    public void testMemoryUsage() throws Exception {
        int ownpid = MonitorUtils.findOwnPid();

        MonitorUtils.MemoryUsage memUsage = MonitorUtils.getMemoryUsage(ownpid);
        assertTrue(memUsage.totalInstances > 0, memUsage.toString());
        assertTrue(memUsage.totalMemoryBytes > 0, memUsage.toString());
        assertEquals(memUsage.getInstanceCounts(), Collections.emptyMap());

        MonitorUtils.MemoryUsage memUsage2 = MonitorUtils.getMemoryUsage(ownpid, MonitorUtilsTest.class.getCanonicalName(),0);
        assertEquals(memUsage2.getInstanceCounts(), ImmutableMap.of(MonitorUtilsTest.class.getCanonicalName(), 1));

        MonitorUtils.MemoryUsage memUsage3 = MonitorUtils.getMemoryUsage(ownpid, MonitorUtilsTest.class.getCanonicalName(), 2);
        assertEquals(memUsage3.getInstanceCounts(), Collections.emptyMap());
    }

    private String getSilentPrefix() {
        if (Os.isMicrosoftWindows()) {
            return "@";
        } else {
            return "";
        }
    }

}
