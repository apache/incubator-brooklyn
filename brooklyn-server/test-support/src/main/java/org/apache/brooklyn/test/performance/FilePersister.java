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
package org.apache.brooklyn.test.performance;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

@Beta
public class FilePersister implements MeasurementResultPersister {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceTestUtils.class);
    
    private final File dir;

    public FilePersister(File dir) {
        this.dir = dir;
    }
    
    @Override
    public void persist(Date date, PerformanceTestDescriptor options, PerformanceTestResult result) {
        try {
            String dateStr = new SimpleDateFormat(Time.DATE_FORMAT_PREFERRED).format(date);
            
            dir.mkdirs();
            
            File file = new File(dir, "auto-test-results.txt");
            file.createNewFile();
            Files.append("date="+dateStr+"; test="+options+"; result="+result+"\n", file, Charsets.UTF_8);

            File summaryFile = new File(dir, "auto-test-summary.txt");
            summaryFile.createNewFile();
            Files.append(
                    dateStr
                            +"\t"+options.summary
                            +"\t"+roundToSignificantFigures(result.ratePerSecond, 6)
                            +"\t"+result.duration
                            +(result.cpuTotalFraction != null ? "\t"+"cpu="+roundToSignificantFigures(result.cpuTotalFraction, 3) : "")
                            +"\n", 
                    summaryFile, Charsets.UTF_8);
            
        } catch (IOException e) {
            LOG.warn("Failed to persist performance results to "+dir+" (continuing)", e);
        }
    }

    // Code copied from http://stackoverflow.com/questions/202302/rounding-to-an-arbitrary-number-of-significant-digits
    private double roundToSignificantFigures(double num, int n) {
        if(num == 0) {
            return 0;
        }

        final double d = Math.ceil(Math.log10(num < 0 ? -num: num));
        final int power = n - (int) d;

        final double magnitude = Math.pow(10, power);
        final long shifted = Math.round(num*magnitude);
        return shifted/magnitude;
    }
}
