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
package brooklyn.entity.group.zoneaware;

import brooklyn.location.Location;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicate;

public class CriticalCauseZoneFailureDetector extends AbstractZoneFailureDetector {

    protected final long timeToConsider;
    protected final Predicate<? super Throwable> criticalityPredicate;
    private final int numTimes;
    
    /**
     * @param timeToConsider       Time for recent attempts (discard any attempts older than this)
     * @param criticalityPredicate What constitutes a critical cause
     * @param numTimes             Number of "critical causes" that must happen within the time period, to consider failed
     */
    public CriticalCauseZoneFailureDetector(Duration timeToConsider, Predicate<? super Throwable> criticalityPredicate, int numTimes) {
        this.timeToConsider = timeToConsider.toMilliseconds();
        this.criticalityPredicate = criticalityPredicate;
        this.numTimes = numTimes;
    }
    
    @Override
    protected boolean doHasFailed(Location loc, ZoneHistory zoneHistory) {
        synchronized (zoneHistory) {
            zoneHistory.trimOlderThan(System.currentTimeMillis() - timeToConsider);
            int count = 0;
            for (Throwable cause : zoneHistory.causes) {
                if (criticalityPredicate.apply(cause)) {
                    count++;
                }
            }
            return count >= numTimes;
        }
    }
}
