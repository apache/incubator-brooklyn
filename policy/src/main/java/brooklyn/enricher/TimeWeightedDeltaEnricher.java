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
package brooklyn.enricher;

import groovy.lang.Closure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.AbstractTypeTransformingEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Functions;

/**
 * Converts an absolute sensor into a delta sensor (i.e. the diff between the current and previous value),
 * presented as a units/timeUnit based on the event timing.
 * <p>
 * NB for time (e.g. "total milliseconds consumed") use {@link TimeFractionDeltaEnricher}
 */
public class TimeWeightedDeltaEnricher<T extends Number> extends AbstractTypeTransformingEnricher<T,Double> {
    private static final Logger LOG = LoggerFactory.getLogger(TimeWeightedDeltaEnricher.class);
    
    Number lastValue;
    long lastTime = -1;
    
    /** unitMillis is the number of milliseconds to apply for the conversion from input to output;
     * e.g. 1000 for counting things per second; 
     * NB for time (e.g. "total milliseconds consumed") use {@link TimeFractionDeltaEnricher} */
    @SetFromFlag
    int unitMillis;
    
    @SetFromFlag
    Function<Double,Double> postProcessor;
    
    // default 1 second
    public static <T extends Number> TimeWeightedDeltaEnricher<T> getPerSecondDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target) {
        return new TimeWeightedDeltaEnricher<T>(producer, source, target, 1000);
    }

    public TimeWeightedDeltaEnricher() { // for rebind
    }
    public TimeWeightedDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, int unitMillis) {
        this(producer, source, target, unitMillis, Functions.<Double>identity());
    }
    public TimeWeightedDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, int unitMillis, Closure<Double> postProcessor) {
        this(producer, source, target, unitMillis, GroovyJavaMethods.<Double,Double>functionFromClosure(postProcessor));
    }
    
    public TimeWeightedDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, int unitMillis, Function<Double,Double> postProcessor) {
        super(producer, source, target);
        this.unitMillis = unitMillis;
        this.postProcessor = postProcessor;
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        onEvent(event, event.getTimestamp());
    }
    
    public void onEvent(SensorEvent<T> event, long eventTime) {
        Number current = event.getValue();
        
        if (current == null) {
            // Can't compute a delta; 
            // don't assume current=zero because then things like requestCount->requestsPerSecond is negative!
            // instead assume same as last time, so delta == 0
            double deltaPostProcessed = postProcessor.apply(0d);
            entity.setAttribute((AttributeSensor<Double>)target, deltaPostProcessed);
            if (LOG.isTraceEnabled()) LOG.trace("set {} to {}, {} -> {} at {}", new Object[] {this, deltaPostProcessed, lastValue, current, eventTime});
            return;
        }
        
        if (eventTime > lastTime) {
            if (lastValue == null) {
                // cannot calculate time-based delta with a single value
                if (LOG.isTraceEnabled()) LOG.trace("{} received event but no last value so will not emit, null -> {} at {}", new Object[] {this, current, eventTime}); 
            } else {
                double duration = (lastTime < 0) ? unitMillis : eventTime - lastTime;
                if (eventTime == lastTime) duration = 0.1; // 0.1 of a millisecond is a relatively small number: 
                double delta = (current.doubleValue() - lastValue.doubleValue()) / (duration / unitMillis);
                double deltaPostProcessed = postProcessor.apply(delta);
                entity.setAttribute((AttributeSensor<Double>)target, deltaPostProcessed);
                if (LOG.isTraceEnabled()) LOG.trace("set {} to {}, {} -> {} at {}", new Object[] {this, deltaPostProcessed, lastValue, current, eventTime}); 
            }
            lastValue = current;
            lastTime = eventTime;
        }
    }
}
