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
package brooklyn.qa.longevity

import static org.testng.Assert.*;


import org.testng.annotations.Test

import com.google.common.base.Charsets
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.io.Files


import brooklyn.qa.longevity.MonitorUtils;
import brooklyn.qa.longevity.MonitorUtilsTest;

class MonitorUtilsTest {

    @Test(enabled=false, groups="UNIX") // Demonstrates that process.waitFor() hangs for big output streams
    public void testGroovyExecuteAndWaitFor() {
        String bigstr = Strings.repeat("a", 100000)
        def process = "echo $bigstr".execute()
        process.waitFor()
        String out = process.text
        assertTrue(out.contains(bigstr), "out.size="+out.length())
    }

    @Test(groups="UNIX")
    public void testGroovyExecuteAndWaitForConsumingOutputStream() {
        String bigstr = Strings.repeat("a", 100000)
        def process = "echo $bigstr".execute()
        String out = MonitorUtils.waitFor(process)
        assertTrue(out.contains(bigstr), "out.size="+out.length())
    }

    @Test(groups="UNIX")
    public void testFindOwnPid() {
        int ownpid = MonitorUtils.findOwnPid()
        assertTrue(ownpid > 0, "ownpid=$ownpid")
        assertTrue(MonitorUtils.isPidRunning(ownpid, "java"),"java is not running")
    }
            
    @Test(groups="UNIX")
    public void testIsPidRunning() {
        int usedPid = MonitorUtils.findOwnPid()
        
        // Find a pid that is in not in use
        // Don't count upwards as that is more likely to be the next pid to be allocated leading to non-deterministic failures!
        // 10000 is a conservative estimate of a legal large pid (/proc/sys/kernel/pid_max gives the real max)
        def process = MonitorUtils.exec("ps ax")
        String out = MonitorUtils.waitFor(process)
        int unusedPid = 10000;
        while (out.contains(""+unusedPid)) {
            unusedPid--;
        }
        if (unusedPid <= 0) throw new IllegalStateException("No unused pid found in the range 1-10000");
         
        assertTrue(MonitorUtils.isPidRunning(usedPid))
        assertFalse(MonitorUtils.isPidRunning(unusedPid))
        assertFalse(MonitorUtils.isPidRunning(1234567)) // too large
    }
    
    @Test(groups="UNIX")
    public void testGetRunningPids() {
        int ownpid = MonitorUtils.findOwnPid()

        List<Integer> javapids = MonitorUtils.getRunningPids("java")

        assertTrue(javapids.contains(ownpid), "javapids="+javapids+"; ownpid="+ownpid)
    }
    
    @Test
    public void testIsUrlUp() {
        assertFalse(MonitorUtils.isUrlUp(new URL("http://localhost/thispathdoesnotexist")))
    }
    
    @Test(groups="UNIX")
    public void testSearchLog() {
        String fileContents = "line1\nline2\nline3\n"
        File file = File.createTempFile("monitorUtilsTest.testSearchLog", ".txt")
        Files.write(fileContents, file, Charsets.UTF_8)
        
        try {
            assertEquals(MonitorUtils.searchLog(file, "line1"), ["line1"])
            assertEquals(MonitorUtils.searchLog(file, "line1|line2"), ["line1", "line2"])
            assertEquals(MonitorUtils.searchLog(file, "textnotthere"), [])
            assertEquals(MonitorUtils.searchLog(file, "line"), ["line1", "line2", "line3"])
        } finally {
            file.delete()
        }
    }
    
    @Test(groups="Integration")
    public void testMemoryUsage() {
        int ownpid = MonitorUtils.findOwnPid()
        
        MonitorUtils.MemoryUsage memUsage = MonitorUtils.getMemoryUsage(ownpid)
        assertTrue(memUsage.totalInstances > 0, memUsage.toString())
        assertTrue(memUsage.totalMemoryBytes > 0, memUsage.toString())
        assertEquals(memUsage.getInstanceCounts(), Collections.emptyMap())
        
        MonitorUtils.MemoryUsage memUsage2 = MonitorUtils.getMemoryUsage(ownpid, MonitorUtilsTest.class.getCanonicalName(),0)
        assertEquals(memUsage2.getInstanceCounts(), ImmutableMap.of(MonitorUtilsTest.class.getCanonicalName(), 1))
        
        MonitorUtils.MemoryUsage memUsage3 = MonitorUtils.getMemoryUsage(ownpid, MonitorUtilsTest.class.getCanonicalName(), 2)
        assertEquals(memUsage3.getInstanceCounts(), Collections.emptyMap())
    }
}
