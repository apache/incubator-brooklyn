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

    @Test(enabled=false) // Demonstrates that process.waitFor() hangs for big output streams
    public void testGroovyExecuteAndWaitFor() {
        String bigstr = Strings.repeat("a", 100000)
        def process = "echo $bigstr".execute()
        process.waitFor()
        String out = process.text
        assertTrue(out.contains(bigstr), "out.size="+out.length())
    }

    @Test
    public void testGroovyExecuteAndWaitForConsumingOutputStream() {
        String bigstr = Strings.repeat("a", 100000)
        def process = "echo $bigstr".execute()
        String out = MonitorUtils.waitFor(process)
        assertTrue(out.contains(bigstr), "out.size="+out.length())
    }

    @Test
    public void testFindOwnPid() {
        int ownpid = MonitorUtils.findOwnPid()
        assertTrue(ownpid > 0, "ownpid=$ownpid")
        assertTrue(MonitorUtils.isPidRunning(ownpid, "java"))
    }
            
    @Test
    public void testIsPidRunning() {
        int usedPid = MonitorUtils.findOwnPid()
        
        // Find a pid that is in not in use
        def process = MonitorUtils.exec("ps ax")
        String out = MonitorUtils.waitFor(process)
        int unusedPid = 1
        while (out.contains(""+unusedPid)) {
            unusedPid++;
        }

        assertTrue(MonitorUtils.isPidRunning(usedPid))
        assertFalse(MonitorUtils.isPidRunning(unusedPid))
        assertFalse(MonitorUtils.isPidRunning(1234567)) // too large
    }
    
    @Test
    public void testGetRunningPids() {
        int ownpid = MonitorUtils.findOwnPid()
        List<Integer> javapids = MonitorUtils.getRunningPids("java")
        
        assertTrue(javapids.contains(ownpid), "javapids="+javapids+"; ownpid="+ownpid)
    }
    
    @Test
    public void testIsUrlUp() {
        assertFalse(MonitorUtils.isUrlUp(new URL("http://localhost/thispathdoesnotexist")))
    }
    
    @Test
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
    
    @Test
    public void testMemoryUsage() {
        int ownpid = MonitorUtils.findOwnPid()
        
        MonitorUtils.MemoryUsage memUsage = MonitorUtils.getMemoryUsage(ownpid)
        assertTrue(memUsage.totalInstances > 0, memUsage.toString())
        assertTrue(memUsage.totalMemoryBytes > 0, memUsage.toString())
        assertEquals(memUsage.getInstanceCounts(), Collections.emptyMap())
        
        MonitorUtils.MemoryUsage memUsage2 = MonitorUtils.getMemoryUsage(ownpid, MonitorUtilsTest.class.getCanonicalName())
        assertEquals(memUsage2.getInstanceCounts(), ImmutableMap.of(MonitorUtilsTest.class.getCanonicalName(), 1))
        
        MonitorUtils.MemoryUsage memUsage3 = MonitorUtils.getMemoryUsage(ownpid, MonitorUtilsTest.class.getCanonicalName(), 2)
        assertEquals(memUsage3.getInstanceCounts(), Collections.emptyMap())
    }
}
