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

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.stream.StreamGobbler;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public class MonitorUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MonitorUtils.class);

    private static final int checkPeriodMs = 1000;

    private static volatile int ownPid = -1;

    /**
     * Confirm can read from URL.
     *
     * @param url
     */
    public static boolean isUrlUp(URL url) {
        try {
            HttpToolResponse result = HttpTool.httpGet(
                    HttpTool.httpClientBuilder().trustAll().build(), 
                    URI.create(url.toString()), 
                    ImmutableMap.<String,String>of());
            int statuscode = result.getResponseCode();

            if (statuscode != 200) {
                LOG.info("Error reading URL {}: {}, {}", new Object[]{url, statuscode, result.getReasonPhrase()});
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            LOG.info("Error reading URL {}: {}", url, e);
            return false;
        }
    }

    public static boolean isPidRunning(int pid) {
        return isPidRunning(pid, null);
    }

    /**
     * Confirm the given pid is running, and that the the process matches the given regex.
     *
     * @param pid
     * @param regex
     */
    public static boolean isPidRunning(int pid, String regex) {
        Process process = exec("ps -p " + pid);
        String out = waitFor(process);
         if (process.exitValue() > 0) {
            String err = toString(process.getErrorStream());
            LOG.info(String.format("pid %s not running: %s", pid, err));
            return false;
        }

        if (regex != null) {
            String regex2 = "^\\s*" + pid + ".*" + regex;
            boolean found = false;
            for (String line : out.split("\n")) {
                if (hasAtLeastOneMatch(line, regex2)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                String txt = toString(process.getInputStream());
                LOG.info("process did not match regular expression: "+txt);
                return false;
            }
        }

        return true;
    }

    private static boolean hasAtLeastOneMatch(String line, String regex) {
        return Pattern.matches(".*"+regex+".*", line);
    }

    private static String toString(InputStream in){
        try {
            byte[] bytes = ByteStreams.toByteArray(in);
            return new String(bytes);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

    }

    public static List<Integer> getRunningPids(String regex) {
        return getRunningPids(regex, null);
    }

    /**
     * Confirm the given pid is running, and that the the process matches the given regex.
     *
     * @param regex
     * @param excludingRegex
     */
    public static List<Integer> getRunningPids(String regex, String excludingRegex) {
        Process process = exec("ps ax");
        String out = waitFor(process);

        List<Integer> result = new LinkedList<Integer>();
        for (String line : out.split("\n")) {
            if (excludingRegex != null && hasAtLeastOneMatch(line, excludingRegex)) {
                continue;
            }
            if (hasAtLeastOneMatch(line, regex)) {
                String[] linesplit = line.trim().split("\\s+");
                result.add(Integer.parseInt(linesplit[0]));
            }
        }
        return result;
    }

    public static MemoryUsage getMemoryUsage(int pid){
          return getMemoryUsage(pid, null,0);
    }

    /**
     * @param pid
     */
    public static MemoryUsage getMemoryUsage(int pid, String clazzRegexOfInterest, int minInstancesOfInterest) {
        Process process = exec(String.format("jmap -histo %s", pid));
        String out = waitFor(process);

        Map<String, Integer> instanceCounts = Maps.newLinkedHashMap();
        long totalInstances=0;
        long totalMemoryBytes=0;

        for (String line : out.split("\n")) {
            if (clazzRegexOfInterest!=null && hasAtLeastOneMatch(line, clazzRegexOfInterest)) {
                // Format is:
                //   num     #instances         #bytes  class name
                //   1:           43506        8047096  example.MyClazz

                String[] parts = line.trim().split("\\s+");
                String clazz = parts[3];
                int instanceCount = Integer.parseInt(parts[1]);
                if (instanceCount >= minInstancesOfInterest) {
                    instanceCounts.put(clazz, instanceCount);
                }
            }
            if (hasAtLeastOneMatch(line, "^Total.*")) {
                String[] parts = line.split("\\s+");
                totalInstances = Long.parseLong(parts[1]);
                totalMemoryBytes = Long.parseLong(parts[2]);
            }
        }

        return new MemoryUsage(totalInstances, totalMemoryBytes, instanceCounts);
    }

    public static class MemoryUsage {
        final long totalInstances;
        final long totalMemoryBytes;
        final Map<String, Integer> instanceCounts;

        MemoryUsage(long totalInstances, long totalMemoryBytes, Map<String, Integer> instanceCounts) {
            this.totalInstances = totalInstances;
            this.totalMemoryBytes = totalMemoryBytes;
            this.instanceCounts = instanceCounts;
        }

        public String toString() {
            return Objects.toStringHelper(this)
                    .add("totalInstances", totalInstances)
                    .add("totalMemoryBytes", totalMemoryBytes)
                    .add("instanceCounts", instanceCounts)
                    .toString();
        }

        public long getTotalInstances() {
            return totalInstances;
        }

        public long getTotalMemoryBytes() {
            return totalMemoryBytes;
        }

        public Map<String, Integer> getInstanceCounts() {
            return instanceCounts;
        }
    }

    public static List<String> searchLog(File file, String grepOfInterest) {
        return searchLog(file, grepOfInterest, new LinkedHashSet<String>());
    }

    /**
     * Find lines in the given file that match given given regex.
     *
     * @param file
     * @param grepOfInterest
     */
    public static List<String> searchLog(File file, String grepOfInterest, Set<String> grepExclusions) {
        Process process = exec(String.format("grep -E %s %s", grepOfInterest, file.getAbsoluteFile()));
        String out = waitFor(process);

        // TODO Annoying that String.split() returns size 1 when empty string; lookup javadoc when back online...
        if (out.length() == 0) return Collections.<String>emptyList();

        List<String> result = new ArrayList<String>();
        for (String line : out.trim().split("\n")) {
            boolean excluded = false;
            for (String exclusion : grepExclusions) {
                if (!isNullOrEmpty(exclusion) && hasAtLeastOneMatch(line, exclusion)) {
                    excluded = true;
                }
            }
            if (!excluded) {
                result.add(line);
            }
        }
        return result;
    }

    public static Process exec(String cmd) {
        LOG.info("executing cmd: " + cmd);

        try {
            return Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Waits for the given process to complete, consuming its stdout and returning it as a string.
     * <p/>
     * Does not just use Groovy's:
     * process.waitFor()
     * return process.text
     * <p/>
     * Because that code hangs for bing output streams.
     */
    public static String waitFor(Process process) {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        StreamGobbler gobbler = new StreamGobbler(process.getInputStream(), bytesOut, null);
        gobbler.start();
        try {
            process.waitFor();
            gobbler.blockUntilFinished();
            return new String(bytesOut.toByteArray());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (gobbler.isAlive()) gobbler.interrupt();
        }
    }

    public static int findOwnPid() throws IOException {
        if (ownPid >= 0) return ownPid;

        String[] cmd = new String[]{"bash", "-c", "echo $PPID"};
        Process process = Runtime.getRuntime().exec(cmd);
        String out = MonitorUtils.waitFor(process);
        ownPid = Integer.parseInt(out.trim());
        return ownPid;
    }
}
