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
package brooklyn.entity.machine;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import brooklyn.config.render.RendererHints;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.guava.Functionals;
import brooklyn.util.math.MathFunctions;
import brooklyn.util.text.ByteSizeStrings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;

public class MachineAttributes {

    /**
     * Do not instantiate.
     */
    private MachineAttributes() {}

    /*
     * Sensor attributes for machines.
     */

    public static final AttributeSensor<Duration> UPTIME = Sensors.newSensor(Duration.class, "machine.uptime", "Current uptime");
    public static final AttributeSensor<Double> LOAD_AVERAGE = Sensors.newDoubleSensor("machine.loadAverage", "Current load average");

    public static final AttributeSensor<Double> CPU_USAGE = Sensors.newDoubleSensor("machine.cpu", "Current CPU usage");
    public static final AttributeSensor<Double> AVERAGE_CPU_USAGE = Sensors.newDoubleSensor("cpu.average", "Average CPU usage across the cluster");

    public static final AttributeSensor<Long> FREE_MEMORY = Sensors.newLongSensor("machine.memory.free", "Current free memory");
    public static final AttributeSensor<Long> TOTAL_MEMORY = Sensors.newLongSensor("machine.memory.total", "Total memory");
    public static final AttributeSensor<Long> USED_MEMORY = Sensors.newLongSensor("machine.memory.used", "Current memory usage");
    public static final AttributeSensor<Double> USED_MEMORY_DELTA_PER_SECOND_LAST = Sensors.newDoubleSensor("memory.used.delta", "Change in memory usage per second");
    public static final AttributeSensor<Double> USED_MEMORY_DELTA_PER_SECOND_IN_WINDOW = Sensors.newDoubleSensor("memory.used.windowed", "Average change in memory usage over 30s");

    private static AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Setup renderer hints.
     */
    public static void init() {
        if (initialized.getAndSet(true)) return;

        final Function<Double, Long> longValue = new Function<Double, Long>() {
            @Override
            public Long apply(@Nullable Double input) {
                if (input == null) return null;
                return input.longValue();
            }
        };

        RendererHints.register(UPTIME, RendererHints.displayValue(Time.toTimeStringRounded()));

        RendererHints.register(CPU_USAGE, RendererHints.displayValue(MathFunctions.percent(2)));
        RendererHints.register(AVERAGE_CPU_USAGE, RendererHints.displayValue(MathFunctions.percent(2)));

        RendererHints.register(FREE_MEMORY, RendererHints.displayValue(Functionals.chain(MathFunctions.times(1000L), ByteSizeStrings.metric())));
        RendererHints.register(TOTAL_MEMORY, RendererHints.displayValue(Functionals.chain(MathFunctions.times(1000L), ByteSizeStrings.metric())));
        RendererHints.register(USED_MEMORY, RendererHints.displayValue(Functionals.chain(MathFunctions.times(1000L), ByteSizeStrings.metric())));
        RendererHints.register(USED_MEMORY_DELTA_PER_SECOND_LAST, RendererHints.displayValue(Functionals.chain(longValue, ByteSizeStrings.metric())));
        RendererHints.register(USED_MEMORY_DELTA_PER_SECOND_IN_WINDOW, RendererHints.displayValue(Functionals.chain(longValue, ByteSizeStrings.metric())));
    }

    static {
        init();
    }
}
