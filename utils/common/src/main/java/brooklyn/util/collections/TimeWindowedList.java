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
package brooklyn.util.collections;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/**
 * Keeps a list of timestamped values that are in the given time-period (millis).
 * It also guarantees to keep the given minimum number of values in the list (even if old),
 * and to keep the given number of out-of-date values.
 * 
 * For example, this is useful if we want to determine if a metric has been consistently high.
 * 
 * @author aled
 */
public class TimeWindowedList<T> {
    private final LinkedList<TimestampedValue<T>> values = new LinkedList<TimestampedValue<T>>();
    private volatile Duration timePeriod;
    private final int minVals;
    private final int minExpiredVals;

    public TimeWindowedList(Duration timePeriod) {
        this.timePeriod = timePeriod;
        minVals = 0;
        minExpiredVals = 0;
    }
    
    /**
     * @deprecated since 0.7.0; use {@link #TimeWindowedList(Duration)}
     */
    public TimeWindowedList(long timePeriod) {
        this(Duration.millis(timePeriod));
    }

    public TimeWindowedList(Map<String,?> flags) {
        if (!flags.containsKey("timePeriod")) throw new IllegalArgumentException("Must define timePeriod");
        timePeriod = Duration.of(flags.get("timePeriod"));
        
        if (flags.containsKey("minVals")) {
            minVals = ((Number)flags.get("minVals")).intValue();
        } else {
            minVals = 0;
        }
        if (flags.containsKey("minExpiredVals")) {
            minExpiredVals = ((Number)flags.get("minExpiredVals")).intValue();
        } else {
            minExpiredVals = 0;
        }
    }
    
    public void setTimePeriod(Duration newTimePeriod) {
        timePeriod = newTimePeriod;
    }
    
    public synchronized T getLatestValue() {
        return (values.isEmpty()) ? null : values.get(values.size()-1).getValue();
    }
    
    public List<TimestampedValue<T>> getValues() {
        return getValues(System.currentTimeMillis());
    }
    
    public synchronized List<TimestampedValue<T>> getValues(long now) {
        pruneValues(now);
        return ImmutableList.copyOf(values);
    }
    
    public synchronized List<TimestampedValue<T>> getValuesInWindow(long now, Duration subTimePeriod) {
        long startTime = now - subTimePeriod.toMilliseconds();
        List<TimestampedValue<T>> result = new LinkedList<TimestampedValue<T>>();
        TimestampedValue<T> mostRecentExpired = null;
        for (TimestampedValue<T> val : values) {
            if (val.getTimestamp() < startTime) {
                // discard; but remember most recent too-old value so we include that as the "initial"
                mostRecentExpired = val;
            } else {
                result.add(val);
            }
        }
        if (minExpiredVals > 0 && mostRecentExpired != null) {
            result.add(0, mostRecentExpired);
        }
        
        if (result.size() < minVals) {
            int minIndex = Math.max(0, values.size()-minVals);
            return ImmutableList.copyOf(values.subList(minIndex, values.size()));
        } else {
            return result;
        }
    }
    
    public void add(T val) {
        add(val, System.currentTimeMillis());
    }
    
    public synchronized void add(T val, long timestamp) {
        values.add(values.size(), new TimestampedValue<T>(val, timestamp));
        pruneValues(timestamp);
    }
    
    public synchronized void pruneValues(long now) {
        long startTime = now - timePeriod.toMilliseconds();
        int expiredValsCount = 0;
        if (timePeriod.equals(Duration.ZERO)) {
            expiredValsCount = values.size();
        } else {
            for (TimestampedValue<T> val : values) {
                if (val.getTimestamp() < startTime) {
                    expiredValsCount++;
                } else {
                    break;
                }
            }
        }
        int numToPrune = Math.min(expiredValsCount - minExpiredVals, values.size()-minVals);
        for (int i = 0; i < numToPrune; i++) {
            values.removeFirst();
        }
    }
    
    @Override
    public String toString() {
        return "timePeriod="+timePeriod+", vals="+values;
    }
}
