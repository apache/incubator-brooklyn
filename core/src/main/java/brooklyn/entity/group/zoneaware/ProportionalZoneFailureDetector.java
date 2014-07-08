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

import com.google.common.base.Ticker;

public class ProportionalZoneFailureDetector extends AbstractZoneFailureDetector {

    // TODO Would be nice to weight it disproportionately for more recent attempts; but this will do for now.
    
    protected final int minDatapoints;
    protected final long timeToConsider;
    protected final double maxProportionFailures;
    
    /**
     * @param minDatapoints         min number of attempts within the time period, to consider this measure reliable
     * @param timeToConsider        time for recent attempts (discard any attempts older than this)
     * @param maxProportionFailures proportion (between 0 and 1) where numFailures/dataPoints >= this number means failure
     */
    public ProportionalZoneFailureDetector(int minDatapoints, Duration timeToConsider, double maxProportionFailures) {
        this(minDatapoints, timeToConsider, maxProportionFailures, Ticker.systemTicker());
    }
    
    public ProportionalZoneFailureDetector(int minDatapoints, Duration timeToConsider, double maxProportionFailures, Ticker ticker) {
        super(ticker);
        this.minDatapoints = minDatapoints;
        this.timeToConsider = timeToConsider.toMilliseconds();
        this.maxProportionFailures = maxProportionFailures;
    }
    
    @Override
    protected boolean doHasFailed(Location loc, ZoneHistory zoneHistory) {
        synchronized (zoneHistory) {
            zoneHistory.trimOlderThan(currentTimeMillis() - timeToConsider);
            int numDatapoints = zoneHistory.successes.size() + zoneHistory.failures.size();
            double proportionFailure = ((double)zoneHistory.failures.size()) / ((double)numDatapoints);
            return numDatapoints >= minDatapoints && proportionFailure >= maxProportionFailures;
        }
    }
}
