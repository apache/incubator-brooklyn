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

import com.google.common.base.Preconditions;

import brooklyn.enricher.basic.AbstractTypeTransformingEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

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
 */
public class RollingTimeWindowMeanEnricher<T extends Number> extends AbstractTypeTransformingEnricher<T,Double> {
    public static class ConfidenceQualifiedNumber {
        final Double value;
        final double confidence;
        
        public ConfidenceQualifiedNumber(Double value, double confidence) {
            this.value = value;
            this.confidence = confidence;
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
        pruneValues(eventTime);
        entity.setAttribute((AttributeSensor<Double>)target, getAverage(eventTime).value); //TODO this can potentially go stale... maybe we need to timestamp as well?
    }
    
    public ConfidenceQualifiedNumber getAverage() {
        return getAverage(System.currentTimeMillis());
    }
    
    public ConfidenceQualifiedNumber getAverage(long now) {
        pruneValues(now);
        if (timestamps.isEmpty()) {
            return lastAverage = new ConfidenceQualifiedNumber(lastAverage.value, 0.0d);
        }

        // XXX grkvlt - see email to development list

        
        long lastTimestamp = timestamps.get(timestamps.size()-1);
        Double confidence = ((double)(timePeriod.toMilliseconds() - (now - lastTimestamp))) / timePeriod.toMilliseconds();
        if (confidence <= 0.0d) {
            double lastValue = values.get(values.size()-1).doubleValue();
            return lastAverage = new ConfidenceQualifiedNumber(lastValue, 0.0d);
        }
        
        long start = (now - timePeriod.toMilliseconds());
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
        while(timestamps.size() > 1 && timestamps.get(0) < (now - timePeriod.toMilliseconds())) {
            timestamps.removeFirst();
            values.removeFirst();
        }
    }
}
