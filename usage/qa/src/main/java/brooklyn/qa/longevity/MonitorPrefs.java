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

import java.io.File;
import java.net.URL;

import com.google.common.base.Objects;
import com.google.common.collect.Range;

public class MonitorPrefs {

    public URL webUrl;
    public int brooklynPid;
    public File logFile;
    public String logGrep;
    public File logGrepExclusionsFile;
    public String webProcessesRegex;
    public Range<Integer> numWebProcesses;
    public int webProcessesCyclingPeriod;
    public File outFile;
    public boolean abortOnError;
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("webUrl", webUrl)
                .add("brooklynPid", brooklynPid)
                .add("logFile", logFile)
                .add("logGrep", logGrep)
                .add("logGrepExclusionsFile", logGrepExclusionsFile)
                .add("outFile", outFile)
                .add("webProcessesRegex", webProcessesRegex)
                .add("numWebProcesses", numWebProcesses)
                .add("webProcessesCyclingPeriod", webProcessesCyclingPeriod)
                .toString();
    }
}
