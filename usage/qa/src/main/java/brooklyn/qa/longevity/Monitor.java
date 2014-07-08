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

import static brooklyn.qa.longevity.StatusRecorder.Factory.chain;
import static brooklyn.qa.longevity.StatusRecorder.Factory.noop;
import static brooklyn.qa.longevity.StatusRecorder.Factory.toFile;
import static brooklyn.qa.longevity.StatusRecorder.Factory.toLog;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.TimeWindowedList;
import brooklyn.util.collections.TimestampedValue;
import brooklyn.util.time.Duration;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.io.Files;

public class Monitor {

    private static final Logger LOG = LoggerFactory.getLogger(Monitor.class);
    
    private static final int checkPeriodMs = 1000;

    private static final OptionParser parser = new OptionParser() {
        {
            acceptsAll(ImmutableList.of("help", "?", "h"), "show help");
            accepts("webUrl", "Web-app url")
                    .withRequiredArg().ofType(URL.class);
            accepts("brooklynPid", "Brooklyn pid")
                    .withRequiredArg().ofType(Integer.class);
            accepts("logFile", "Brooklyn log file")
                    .withRequiredArg().ofType(File.class);
            accepts("logGrep", "Grep in log file (defaults to 'SEVERE|ERROR|WARN|Exception|Error'")
                    .withRequiredArg().ofType(String.class);
            accepts("logGrepExclusionsFile", "File of expressions to be ignored in log file")
                    .withRequiredArg().ofType(File.class);
            accepts("webProcesses", "Name (for `ps ax | grep` of web-processes")
                    .withRequiredArg().ofType(String.class);
            accepts("numWebProcesses", "Number of web-processes expected (e.g. 1 or 1-3)")
                    .withRequiredArg().ofType(String.class);
            accepts("webProcessesCyclingPeriod", "The period (in seconds) for cycling through the range of numWebProcesses")
                    .withRequiredArg().ofType(Integer.class);
            accepts("outFile", "File to write monitor status info")
                    .withRequiredArg().ofType(File.class);
            accepts("abortOnError", "Exit the JVM on error, with exit code 1")
                    .withRequiredArg().ofType(Boolean.class);
        }
    };

    public static void main(String[] argv) throws InterruptedException, IOException {
        OptionSet options = parse(argv);

        if (options == null || options.has("help")) {
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        MonitorPrefs prefs = new MonitorPrefs();
        prefs.webUrl = options.hasArgument("webUrl") ? (URL) options.valueOf("webUrl") : null;
        prefs.brooklynPid = options.hasArgument("brooklynPid") ? (Integer) options.valueOf("brooklynPid") : -1;
        prefs.logFile = options.hasArgument("logFile") ? (File) options.valueOf("logFile") : null;
        prefs.logGrep = options.hasArgument("logGrep") ? (String) options.valueOf("logGrep") : "SEVERE|ERROR|WARN|Exception|Error";
        prefs.logGrepExclusionsFile = options.hasArgument("logGrepExclusionsFile") ? (File) options.valueOf("logGrepExclusionsFile") : null;
        prefs.webProcessesRegex = options.hasArgument("webProcesses") ? (String) options.valueOf("webProcesses") : null;
        prefs.numWebProcesses = options.hasArgument("numWebProcesses") ? parseRange((String) options.valueOf("numWebProcesses")) : null;
        prefs.webProcessesCyclingPeriod = options.hasArgument("webProcessesCyclingPeriod") ? (Integer) options.valueOf("webProcessesCyclingPeriod") : -1;
        prefs.outFile = options.hasArgument("outFile") ? (File) options.valueOf("outFile") : null;
        prefs.abortOnError = options.hasArgument("abortOnError") ? (Boolean) options.valueOf("abortOnError") : false;
        Monitor main = new Monitor(prefs, MonitorListener.NOOP);
        main.start();
    }

    private static Range<Integer> parseRange(String range) {
        if (range.contains("-")) {
            String[] parts = range.split("-");
            return Range.closed(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } else {
            return Range.singleton(Integer.parseInt(range));
        }
    }
    
    private static OptionSet parse(String...argv) {
        try {
            return parser.parse(argv);
        } catch (Exception e) {
            System.out.println("Error in parsing options: " + e.getMessage());
            return null;
        }
    }

    private final MonitorPrefs prefs;
    private final StatusRecorder recorder;
    private final MonitorListener listener;
    
    public Monitor(MonitorPrefs prefs, MonitorListener listener) {
        this.prefs = prefs;
        this.listener = listener;
        this.recorder = chain(toLog(LOG), (prefs.outFile != null ? toFile(prefs.outFile) : noop()));
    }

    private void start() throws IOException {
        LOG.info("Monitoring: "+prefs);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        final AtomicReference<List<String>> previousLogLines = new AtomicReference<List<String>>(Collections.<String>emptyList());
        final TimeWindowedList<Integer> numWebProcessesHistory = new TimeWindowedList<Integer>(
                ImmutableMap.of("timePeriod", Duration.seconds(prefs.webProcessesCyclingPeriod), "minExpiredVals", 1));
        final Set<String> logGrepExclusions = ImmutableSet.copyOf(Files.readLines(prefs.logGrepExclusionsFile, Charsets.UTF_8));
        
        executor.scheduleAtFixedRate(new Runnable() {
                @Override public void run() {
                    StatusRecorder.Record record = new StatusRecorder.Record();
                    StringBuilder failureMsg = new StringBuilder();
                    try {
                        if (prefs.brooklynPid > 0) {
                            boolean pidRunning = MonitorUtils.isPidRunning(prefs.brooklynPid, "java");
                            MonitorUtils.MemoryUsage memoryUsage = MonitorUtils.getMemoryUsage(prefs.brooklynPid, ".*brooklyn.*", 1000);
                            record.put("pidRunning", pidRunning);
                            record.put("totalMemoryBytes", memoryUsage.getTotalMemoryBytes());
                            record.put("totalMemoryInstances", memoryUsage.getTotalInstances());
                            record.put("instanceCounts", memoryUsage.getInstanceCounts());
                            
                            if (!pidRunning) {
                                failureMsg.append("pid "+prefs.brooklynPid+" is not running"+"\n");
                            }
                        }
                        if (prefs.webUrl != null) {
                            boolean webUrlUp = MonitorUtils.isUrlUp(prefs.webUrl);
                            record.put("webUrlUp", webUrlUp);
                            
                            if (!webUrlUp) {
                                failureMsg.append("web URL "+prefs.webUrl+" is not available"+"\n");
                            }
                        }
                        if (prefs.logFile != null) {
                            List<String> logLines = MonitorUtils.searchLog(prefs.logFile, prefs.logGrep, logGrepExclusions);
                            List<String> newLogLines = getAdditions(previousLogLines.get(), logLines);
                            previousLogLines.set(logLines);
                            record.put("logLines", newLogLines);
                            
                            if (newLogLines.size() > 0) {
                                failureMsg.append("Log contains warnings/errors: "+newLogLines+"\n");
                            }
                        }
                        if (prefs.webProcessesRegex != null) {
                            List<Integer> pids = MonitorUtils.getRunningPids(prefs.webProcessesRegex, "--webProcesses");
                            pids.remove((Object)MonitorUtils.findOwnPid());
                            
                            record.put("webPids", pids);
                            record.put("numWebPids", pids.size());
                            numWebProcessesHistory.add(pids.size());
                            
                            if (prefs.numWebProcesses != null) {
                                boolean numWebPidsInRange = prefs.numWebProcesses.apply(pids.size());
                                record.put("numWebPidsInRange", numWebPidsInRange);
                                
                                if (!numWebPidsInRange) {
                                    failureMsg.append("num web processes out-of-range: pids="+pids+"; size="+pids.size()+"; expected="+prefs.numWebProcesses);
                                }
                            
                                if (prefs.webProcessesCyclingPeriod > 0) {
                                    List<TimestampedValue<Integer>> values = numWebProcessesHistory.getValues();
                                    long valuesTimeRange = (values.get(values.size()-1).getTimestamp() - values.get(0).getTimestamp());
                                    if (values.size() > 0 && valuesTimeRange > SECONDS.toMillis(prefs.webProcessesCyclingPeriod)) {
                                        int min = -1;
                                        int max = -1;
                                        for (TimestampedValue<Integer> val : values) {
                                            min = (min < 0) ? val.getValue() : Math.min(val.getValue(), min);
                                            max = Math.max(val.getValue(), max);
                                        }
                                        record.put("minWebSizeInPeriod", min);
                                        record.put("maxWebSizeInPeriod", max);
                                        
                                        if (min > prefs.numWebProcesses.lowerEndpoint() || max < prefs.numWebProcesses.upperEndpoint()) {
                                            failureMsg.append("num web processes not increasing/decreasing correctly: " +
                                            		"pids="+pids+"; size="+pids.size()+"; cyclePeriod="+prefs.webProcessesCyclingPeriod+
                                            		"; expectedRange="+prefs.numWebProcesses+"; min="+min+"; max="+max+"; history="+values);
                                        }
                                    } else {
                                        int numVals = values.size();
                                        long startTime = (numVals > 0) ? values.get(0).getTimestamp() : 0;
                                        long endTime = (numVals > 0) ? values.get(values.size()-1).getTimestamp() : 0;
                                        LOG.info("Insufficient vals in time-window to determine cycling behaviour over period ("+prefs.webProcessesCyclingPeriod+"secs): "+
                                                "numVals="+numVals+"; startTime="+startTime+"; endTime="+endTime+"; periodCovered="+(endTime-startTime)/1000);
                                    }
                                }
                            }
                        }
                        
                    } catch (Throwable t) {
                        LOG.error("Error during periodic checks", t);
                        throw Throwables.propagate(t);
                    }
                    
                    try {
                        recorder.record(record);
                        listener.onRecord(record);
                        
                        if (failureMsg.length() > 0) {
                            listener.onFailure(record, failureMsg.toString());
                            
                            if (prefs.abortOnError) {
                                LOG.error("Aborting on error: "+failureMsg);
                                System.exit(1);
                            }
                        }
                        
                    } catch (Throwable t) {
                        LOG.warn("Error recording monitor info ("+record+")", t);
                        throw Throwables.propagate(t);
                    }
                }
            }, 0, checkPeriodMs, TimeUnit.MILLISECONDS);
    }
    
    // TODO What is the guava equivalent? Don't want Set.difference, because duplicates/ordered.
    private static List<String> getAdditions(List<String> prev, List<String> next) {
        List<String> result = Lists.newArrayList(next);
        result.removeAll(prev);
        return result;
    }
}
