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
package org.apache.brooklyn.policy.enricher;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.sensor.enricher.AbstractTypeTransformingEnricher;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.time.Duration;

/**
 * Converts an absolute measure of time into a fraction of time, based on the delta between consecutive values 
 * and the elapsed time between those values.
 * 
 * For example, if the values are for ProcessCpuTime (i.e. microseconds of CPU time for the given process), then 
 * this would convert it into a fraction (i.e. 100th of the percentage): (newVal-prevVal)/(newTimestamp/prevTimestamp).
 * 
 * It also configured with the time units for the values.
 */
//@Catalog(name="Time-fraction Delta", description="Converts an absolute measure of time into a fraction of time, "
//        + "based on the delta between consecutive values and the elapsed time between those values.")
public class TimeFractionDeltaEnricher<T extends Number> extends AbstractTypeTransformingEnricher<T,Double> {
    private static final Logger LOG = LoggerFactory.getLogger(TimeFractionDeltaEnricher.class);
    
    @SetFromFlag
    private long nanosPerOrigUnit;
    
    protected Number lastValue;
    protected long lastTimestamp = -1;

    public TimeFractionDeltaEnricher() { // for rebinding
    }
    
    public TimeFractionDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, TimeUnit origUnits) {
        this(producer, source, target, origUnits.toNanos(1));
    }
    
    public TimeFractionDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, long nanosPerOrigUnit) {
        super(producer, source, target);
        this.nanosPerOrigUnit = nanosPerOrigUnit;
        
        if (source!=null && target!=null)
            this.uniqueTag = JavaClassNames.simpleClassName(getClass())+":"+source.getName()+"*"+Duration.nanos(nanosPerOrigUnit)+"->"+target.getName();
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        onEvent(event, event.getTimestamp());
    }
    
    public void onEvent(SensorEvent<T> event, long eventTimestamp) {
        Number current = event.getValue();
        
        if (current == null) {
            // Can't compute a delta; 
            // don't assume current=zero because then things like requestCount->requestsPerSecond is negative!
            // instead don't publish anything
            if (LOG.isTraceEnabled()) LOG.trace("ignoring null value in {}, at {}", new Object[] {this, eventTimestamp});
            return;
        }
        
        if (eventTimestamp > lastTimestamp) {
            if (lastValue == null) {
                // cannot calculate delta with a single value
                if (LOG.isTraceEnabled()) LOG.trace("{} received event but no last value so will not emit, null -> {} at {}", 
                        new Object[] {this, current, eventTimestamp}); 
            } else if (lastTimestamp < 0) {
                LOG.warn("{} has lastValue {} but last timestamp {}; new value is {} at {}; not publishing", 
                        new Object[] {this, lastValue, lastTimestamp, current, eventTimestamp});
            } else {
                long duration = eventTimestamp - lastTimestamp;
                double fraction = toNanos(current.doubleValue() - lastValue.doubleValue(), nanosPerOrigUnit) / TimeUnit.MILLISECONDS.toNanos(duration);
                entity.setAttribute((AttributeSensor<Double>)target, fraction);
                if (LOG.isTraceEnabled()) LOG.trace("set {} to {}, {} -> {} at {} (previous at {})", 
                        new Object[] {this, fraction, lastValue, current, eventTimestamp, lastTimestamp}); 
            }
            lastValue = current;
            lastTimestamp = eventTimestamp;
        }
    }
    
    private double toNanos(double val, long nanosPerOrigUnit) {
        return val*nanosPerOrigUnit;
    }
}
