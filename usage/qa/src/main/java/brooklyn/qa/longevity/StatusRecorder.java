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
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public interface StatusRecorder {

    public void record(Record record) throws IOException;
    
    public static class Factory {
        public static final StatusRecorder NOOP = new StatusRecorder() {
            @Override public void record(Record record) {}
        };
        
        public static StatusRecorder noop() {
            return NOOP;
        }
        public static StatusRecorder toFile(File outFile) {
            return new FileBasedStatusRecorder(outFile);
        }
        public static StatusRecorder toSysout() {
            return new SysoutBasedStatusRecorder();
        }
        public static StatusRecorder toLog(Logger log) {
            return new LogBasedStatusRecorder(log);
        }
        public static StatusRecorder chain(StatusRecorder...recorders) {
            return new ChainingStatusRecorder(recorders);
        }
    }
    
    public static class Record {
        private final Map<String,Object> fields = Maps.newLinkedHashMap();
        
        public void putAll(Map<String,?> entries) {
            fields.putAll(entries);
        }
        
        public void putAll(String keyPrefix, Map<String,?> entries) {
            for (Map.Entry<String,?> entry : entries.entrySet()) {
                fields.put(keyPrefix+entry.getKey(), entry.getValue());
            }
        }
        
        public void put(String key, Object val) {
            fields.put(key, val);
        }
        
        @Override
        public String toString() {
            return fields.toString();
        }
    }
    
    public static class FileBasedStatusRecorder implements StatusRecorder {
        private final File outFile;
    
        public FileBasedStatusRecorder(File outFile) {
            this.outFile = outFile;
        }
        
        @Override
        public void record(Record record) throws IOException {
            Files.append(record.fields.toString()+"\n", outFile, Charsets.UTF_8);
        }
    }
    
    public static class SysoutBasedStatusRecorder implements StatusRecorder {
        public SysoutBasedStatusRecorder() {
        }
        
        @Override
        public void record(Record record) {
            System.out.println(record.fields);
        }
    }
    
    public static class LogBasedStatusRecorder implements StatusRecorder {
        private final Logger log;

        public LogBasedStatusRecorder(Logger log) {
            this.log = log;
        }
        
        @Override
        public void record(Record record) {
            log.info("{}", record.fields);
        }
    }
    
    public static class ChainingStatusRecorder implements StatusRecorder {
        private final StatusRecorder[] recorders;

        public ChainingStatusRecorder(StatusRecorder... recorders) {
            this.recorders = recorders;
        }
        
        @Override
        public void record(Record record) throws IOException {
            for (StatusRecorder recorder : recorders) {
                recorder.record(record);
            }
        }
    }
}
