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
package brooklyn.entity.java;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

public interface UsesJavaMXBeans {

    @SetFromFlag("mxbeanStatsEnabled")
    ConfigKey<Boolean> MXBEAN_STATS_ENABLED =
            ConfigKeys.newBooleanConfigKey("java.metrics.mxbeanStatsEnabled", "Enables collection of JVM stats from the MXBeans, such as memory and thread usage (default is true)", true);

    AttributeSensor<Long> USED_HEAP_MEMORY =
            Sensors.newLongSensor("java.metrics.heap.used", "Current heap size (bytes)");
    AttributeSensor<Long> INIT_HEAP_MEMORY =
            Sensors.newLongSensor("java.metrics.heap.init", "Initial heap size (bytes)");
    AttributeSensor<Long> COMMITTED_HEAP_MEMORY =
            Sensors.newLongSensor("java.metrics.heap.committed", "Commited heap size (bytes)");
    AttributeSensor<Long> MAX_HEAP_MEMORY =
            Sensors.newLongSensor("java.metrics.heap.max", "Max heap size (bytes)");
    AttributeSensor<Long> NON_HEAP_MEMORY_USAGE =
            Sensors.newLongSensor("java.metrics.nonheap.used", "Current non-heap size (bytes)");
    AttributeSensor<Integer> CURRENT_THREAD_COUNT =
            Sensors.newIntegerSensor( "java.metrics.threads.current", "Current number of threads");
    AttributeSensor<Integer> PEAK_THREAD_COUNT =
            Sensors.newIntegerSensor("java.metrics.threads.max", "Peak number of threads");

    // runtime system attributes
    AttributeSensor<Long> START_TIME =
            Sensors.newLongSensor("java.metrics.starttime", "Start time of Java process (UTC)");
    AttributeSensor<Long> UP_TIME =
            Sensors.newLongSensor("java.metrics.uptime", "Uptime of Java process (millis, elapsed since start)");
    
    //operating system attributes
    AttributeSensor<Double> PROCESS_CPU_TIME = Sensors.newDoubleSensor( 
            "java.metrics.processCpuTime.total", "Process CPU time (total millis since start)");
    AttributeSensor<Double> PROCESS_CPU_TIME_FRACTION_LAST = Sensors.newDoubleSensor( 
            "java.metrics.processCpuTime.fraction.last", "Fraction of CPU time used, reported by JVM (percentage, last datapoint)");
    AttributeSensor<Double> PROCESS_CPU_TIME_FRACTION_IN_WINDOW = Sensors.newDoubleSensor( 
            "java.metrics.processCpuTime.fraction.windowed", "Fraction of CPU time used, reported by JVM (percentage, over time window)");
    
    AttributeSensor<Integer> AVAILABLE_PROCESSORS =
            Sensors.newIntegerSensor("java.metrics.processors.available", "number of processors available to the Java virtual machine");
    AttributeSensor<Double> SYSTEM_LOAD_AVERAGE
            = Sensors.newDoubleSensor("java.metrics.systemload.average", "average system load");
    AttributeSensor<Long> TOTAL_PHYSICAL_MEMORY_SIZE =
            Sensors.newLongSensor("java.metrics.physicalmemory.total", "The physical memory available to the operating system");
    AttributeSensor<Long> FREE_PHYSICAL_MEMORY_SIZE =
            Sensors.newLongSensor("java.metrics.physicalmemory.free", "The free memory available to the operating system");

    // GC attributes
    AttributeSensor<Map> GARBAGE_COLLECTION_TIME = new BasicAttributeSensor<Map>(Map.class, "java.metrics.gc.time", "garbage collection time");

}