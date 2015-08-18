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

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.sensor.enricher.AbstractTypeTransformingEnricher;
import org.apache.brooklyn.sensor.enricher.YamlRollingTimeWindowMeanEnricher;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * Transforms {@link Sensor} data into a rolling average based on a time window.
 * 
 * All values within the window are weighted or discarded based on the timestamps associated with
 * them (discards occur when a new value is added or an average is requested)
 * <p>
 * This will not extrapolate figures - it is assumed a value is valid and correct for the entire
 * time period between it and the previous value. Normally, the average attribute is only updated
 * when a new value arrives so it can give a fully informed average, but there is a danger of this
 * going stale.
 * <p>
 * When an average is requested, it is likely there will be a segment of the window for which there
 * isn't a value. Instead of extrapolating a value and providing different extrapolation techniques,
 * the average is reported with a confidence value which reflects the fraction of the time
 * window for which the values were valid.
 * <p>
 * Consumers of the average may ignore the confidence value and just use the last known average.
 * They could multiply the returned value by the confidence value to get a decay-type behavior as
 * the window empties. A third alternative is to, at a certain confidence threshold, report that
 * the average is no longer meaningful.
 * <p>
 * The default average when no data has been received is 0, with a confidence of 0
 * <p>
 * TODO this may end up being deprecated in favour of near-duplicate code in YAML-friendly {@link YamlRollingTimeWindowMeanEnricher},
 * marking as @Beta in 0.7.0 timeframe 
 */
@Beta
//@Catalog(name="Rolling Mean in Time Window", description="Transforms a sensor's data into a rolling average "
//        + "based on a time window.")
public class RollingTimeWindowMeanEnricher<T extends Number> extends AbstractTypeTransformingEnricher<T,Double> {
    
    public static ConfigKey<Double> CONFIDENCE_REQUIRED_TO_PUBLISH = ConfigKeys.newDoubleConfigKey("confidenceRequired",
        "Minimum confidence level (ie period covered) required to publish a rolling average", 0.8d);

    public static class ConfidenceQualifiedNumber {
        final Double value;
        final double confidence;
        
        public ConfidenceQualifiedNumber(Double value, double confidence) {
            this.value = value;
            this.confidence = confidence;
        }
        
        @Override
        public String toString() {
            return ""+value+" ("+(int)(confidence*100)+"%)";
        } 
        
    }
    
    private final LinkedList<T> values = new LinkedList<T>();
    private final LinkedList<Long> timestamps = new LinkedList<Long>();
    volatile ConfidenceQualifiedNumber lastAverage = new ConfidenceQualifiedNumber(0d,0d);
    
    @SetFromFlag
    Duration timePeriod;

    public RollingTimeWindowMeanEnricher() { // for rebinding
    }

    public RollingTimeWindowMeanEnricher(Entity producer, AttributeSensor<T> source, 
        AttributeSensor<Double> target, Duration timePeriod) {
        super(producer, source, target);
        this.timePeriod = Preconditions.checkNotNull(timePeriod, "timePeriod");
        
        if (source!=null && target!=null)
            this.uniqueTag = JavaClassNames.simpleClassName(getClass())+":"+source.getName()+"/"+timePeriod+"->"+target.getName();
    }

    /** @deprecated since 0.6.0 use Duration parameter rather than long with millis */
    public RollingTimeWindowMeanEnricher(Entity producer, AttributeSensor<T> source, 
            AttributeSensor<Double> target, long timePeriod) {
        this(producer, source, target, Duration.millis(timePeriod));
    }


    @Override
    public void onEvent(SensorEvent<T> event) {
        onEvent(event, event.getTimestamp());
    }
    
    public void onEvent(SensorEvent<T> event, long eventTime) {
        values.addLast(event.getValue());
        timestamps.addLast(eventTime);
        if (eventTime>0) {
            ConfidenceQualifiedNumber average = getAverage(eventTime, 0);

            if (average.confidence > getConfig(CONFIDENCE_REQUIRED_TO_PUBLISH)) { 
                // without confidence, we might publish wildly varying estimates,
                // causing spurious resizes, so allow it to be configured, and
                // by default require a high value

                // TODO would be nice to include timestamp, etc
                entity.setAttribute((AttributeSensor<Double>)target, average.value); 
            }
        }
    }
    
    @Deprecated /** @deprecated since 0.7.0; not used except in groovy tests; use the 2-arg method */
    public ConfidenceQualifiedNumber getAverage() {
        return getAverage(System.currentTimeMillis(), 0);
    }
    
    @Deprecated /** @deprecated since 0.7.0; not used except in groovy tests; use the 2-arg method */
    public ConfidenceQualifiedNumber getAverage(long fromTimeExact) {
        return getAverage(fromTimeExact, 0);
    }
    
    public ConfidenceQualifiedNumber getAverage(long fromTime, long graceAllowed) {
        if (timestamps.isEmpty()) {
            return lastAverage = new ConfidenceQualifiedNumber(lastAverage.value, 0.0d);
        }
        
        long firstTimestamp = -1;
        Iterator<Long> ti = timestamps.iterator();
        while (ti.hasNext()) {
            firstTimestamp = ti.next();
            if (firstTimestamp>0) break;
        }
        if (firstTimestamp<=0) {
            // no values with reasonable timestamps
            return lastAverage = new ConfidenceQualifiedNumber(values.get(values.size()-1).doubleValue(), 0.0d);
        }

        long lastTimestamp = timestamps.get(timestamps.size()-1);

        long now = fromTime;
        if (lastTimestamp > fromTime - graceAllowed) {
            // without this, if the computation takes place X seconds after the publish,
            // we treat X seconds as time for which we have no confidence in the data
            now = lastTimestamp;
        }
        pruneValues(now);
        
        long windowStart = Math.max(now-timePeriod.toMilliseconds(), firstTimestamp);
        long windowEnd = Math.max(now-timePeriod.toMilliseconds(), lastTimestamp);
        Double confidence = ((double)(windowEnd - windowStart)) / timePeriod.toMilliseconds();
        if (confidence <= 0.0000001d) {
            // not enough timestamps in window 
            double lastValue = values.get(values.size()-1).doubleValue();
            return lastAverage = new ConfidenceQualifiedNumber(lastValue, 0.0d);
        }
        
        long start = windowStart;
        long end;
        double weightedAverage = 0.0d;
        
        Iterator<T> valuesIter = values.iterator();
        Iterator<Long> timestampsIter = timestamps.iterator();
        while (valuesIter.hasNext()) {
            // Ignores null and out-of-date values (and also values that are received out-of-order, but that shouldn't happen!)
            Number val = valuesIter.next();
            Long timestamp = timestampsIter.next();
            if (val!=null && timestamp >= start) {
                end = timestamp;
                weightedAverage += ((end - start) / (confidence * timePeriod.toMilliseconds())) * val.doubleValue();
                start = timestamp;
            }
        }
        
        return lastAverage = new ConfidenceQualifiedNumber(weightedAverage, confidence);
    }
    
    /**
     * Discards out-of-date values, but keeps at least one value.
     */
    private void pruneValues(long now) {
        // keep one value from before the period, so that we can tell the window's start time 
        while(timestamps.size() > 1 && timestamps.get(1) < (now - timePeriod.toMilliseconds())) {
            timestamps.removeFirst();
            values.removeFirst();
        }
    }
}
