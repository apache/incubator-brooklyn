package brooklyn.qa.longevity



import java.io.IOException;
import java.net.URL


import org.apache.http.HttpResponse
import org.apache.http.StatusLine
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.util.internal.StreamGobbler

import com.google.common.base.Objects;
import com.google.common.collect.Maps


public class MonitorUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MonitorUtils.class)
    
    private static final int checkPeriodMs = 1000;

    private static volatile int ownPid = -1;
    
    /**
     * Confirm can read from URL.
     * 
     * @param url
     */
    public static boolean isUrlUp(URL url) {
        try {
            HttpClient httpclient = new DefaultHttpClient();
            HttpGet httpget = new HttpGet(new URI(url.toString()));
            HttpResponse response = httpclient.execute(httpget);
            StatusLine statusLine = response.getStatusLine()
            int statuscode = statusLine.getStatusCode()
    
            if (statuscode != 200) {
                LOG.info("Error reading URL {}: {}, {}", url, statuscode, statusLine.getReasonPhrase())
                return false
            } else {
                return true
            }
        } catch (Exception e) {
            LOG.info("Error reading URL {}: {}", url, e)
            return false
        }
    }

    /**
     * Confirm the given pid is running, and that the the process matches the given regex.
     * 
     * @param pid
     * @param regex
     */
    public static boolean isPidRunning(int pid, String regex=null) {
        Process process = exec("ps -p $pid")
        String out = waitFor(process)
        if (process.exitValue() > 0) {
            LOG.info("pid $pid not running: ${process.err.text}")
            return false
        }
        
        if (regex) {
            String regex2 = "^\\s*"+pid+".*"+regex
            boolean found = false
            for (String line : out.split("\n")) {
                if (line =~ regex2) {
                    found = true
                    break
                }
            }
            
            if (!found) {
                LOG.info("process did not match regular expression: ${process.text}")
                return false
            }
        }

        return true
    }
    
    /**
     * Confirm the given pid is running, and that the the process matches the given regex.
     * 
     * @param pid
     * @param regex
     */
    public static List<Integer> getRunningPids(String regex, String excludingRegex=null) {
        Process process = exec("ps ax")
        String out = waitFor(process)

        List<String> result = []        
        for (String line : out.split("\n")) {
            if (excludingRegex != null && line =~ excludingRegex) {
                continue;
            }
            if (line =~ regex) {
                String[] linesplit = line.trim().split("\\s+")
                result.add(Integer.parseInt(linesplit[0]))
            }
        }
        return result
    }
    
    /**
     * 
     * @param pid
     */
    public static MemoryUsage getMemoryUsage(int pid, String clazzRegexOfInterest=null, int minInstancesOfInterest=0) {
        def process = exec("jmap -histo $pid")
        String out = waitFor(process)
        
        Map<String,Integer> instanceCounts = Maps.newLinkedHashMap();
        long totalInstances
        long totalMemoryBytes
        
        for (String line : out.split("\n")) {
            if (clazzRegexOfInterest && line =~ clazzRegexOfInterest) {
                // Format is:
                //   num     #instances         #bytes  class name
                //   1:           43506        8047096  example.MyClazz
                
                String[] parts = line.trim().split("\\s+")
                String clazz = parts[3]
                int instanceCount = Integer.parseInt(parts[1])
                if (instanceCount >= minInstancesOfInterest) {
                    instanceCounts.put(clazz, instanceCount)
                }
            }
            if (line =~ "^Total.*") {
                String[] parts = line.split("\\s+")
                totalInstances = Long.parseLong(parts[1])
                totalMemoryBytes = Long.parseLong(parts[2])
            }
        }
        
        return new MemoryUsage(totalInstances, totalMemoryBytes, instanceCounts)
    }
    
    public static class MemoryUsage {
        final long totalInstances
        final long totalMemoryBytes
        final Map<String,Integer> instanceCounts
        
        MemoryUsage(long totalInstances, long totalMemoryBytes, Map<String,Integer> instanceCounts) {
            this.totalInstances = totalInstances
            this.totalMemoryBytes = totalMemoryBytes
            this.instanceCounts = instanceCounts
        }
        
        public String toString() {
            Objects.toStringHelper(this)
                    .add("totalInstances", totalInstances)
                    .add("totalMemoryBytes", totalMemoryBytes)
                    .add("instanceCounts", instanceCounts)
                    .toString()
        }
    }
    
    /**
     * Find lines in the given file that match given given regex.
     * 
     * @param file
     * @param grepOfInterest
     */
    public static List<String> searchLog(File file, String grepOfInterest, Set<String> grepExclusions=[]) {
        def process = exec("grep -E $grepOfInterest ${file.absolutePath}")
        String out = waitFor(process)

        // TODO Annoying that String.split() returns size 1 when empty string; lookup javadoc when back online...
        if (out.length() == 0) return Collections.<String>emptyList()
        
        List<String> result = new ArrayList<String>()
        for (String line : out.trim().split("\n")) {
            boolean excluded = false
            for (String exclusion : grepExclusions) {
                if (exclusion && line =~ exclusion) {
                    excluded = true
                }
            }
            if (!excluded) {
                result.add(line)
            }
        }
        return result
    }
    
    public static Process exec(String cmd) {
        LOG.info("executing cmd: $cmd")
        return cmd.execute()
    }

    /**
     * Waits for the given process to complete, consuming its stdout and returning it as a string.
     * 
     * Does not just use Groovy's:
     *   process.waitFor()
     *   return process.text
     * 
     * Because that code hangs for bing output streams.
     */
    public static String waitFor(Process process) {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream()
        StreamGobbler gobbler = new StreamGobbler(process.getInputStream(), bytesOut, null)
        gobbler.start()
        try {
            process.waitFor()
            gobbler.blockUntilFinished()
            return new String(bytesOut.toByteArray())
        } finally {
            if (gobbler.isAlive()) gobbler.interrupt()
        }
    }

    public static int findOwnPid() throws IOException {
        if (ownPid >= 0) return ownPid
        
        String[] cmd = ["bash", "-c", "echo \$PPID"]
        Process process = Runtime.getRuntime().exec(cmd);
        String out = MonitorUtils.waitFor(process)
        ownPid = Integer.parseInt(out.trim())
        return ownPid
    }
}
