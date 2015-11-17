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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.collect.Maps;

/**
 * A simplistic histogram to store times in a number of buckets.
 * The buckets are in nanoseconds, increasing in size in powers of two.
 */
@Beta
public class Histogram {

    // TODO Currently just does toString to get the values back out.
    
    private final Map<Integer, Integer> counts = Maps.newLinkedHashMap();

    public void add(long val, TimeUnit unit) {
        add(unit.toNanos(val));
    }

    public void add(Duration val) {
        add(val.toNanoseconds());
    }

    protected void add(long val) {
        if (val < 0) throw new UnsupportedOperationException("Negative numbers not accepted: "+val);
        int pow = getPower(val);
        Integer count = counts.get(pow);
        counts.put(pow, (count == null) ? 1 : count+1);
    }

    protected int getPower(long val) {
        for (int i = 0; i < 64; i++) {
            if (val < Math.pow(2, i)) {
                return i;
            }
        }
        return 64;
    }
    
    @Override
    public String toString() {
        if (counts.isEmpty()) return "<empty>";
        
        StringBuilder result = new StringBuilder("{");
        List<Integer> sortedPows = MutableList.copyOf(counts.keySet());
        Collections.sort(sortedPows);
        int minPow = sortedPows.get(0);
        int maxPow = sortedPows.get(sortedPows.size()-1);
        for (int i = minPow; i <= maxPow; i++) {
            if (i != minPow) result.append(", ");
            long lower = i == 0 ? 0 : (long) Math.pow(2, i-1);
            long upper = (long) Math.pow(2, i);
            Integer count = counts.get(i);
            result.append(Time.makeTimeStringRounded(lower, TimeUnit.NANOSECONDS) 
                    + "-" + Time.makeTimeStringRounded(upper, TimeUnit.NANOSECONDS) 
                    + ": " + (count == null ? 0 : count));
        }
        result.append("}");
        return result.toString();
    }
}
