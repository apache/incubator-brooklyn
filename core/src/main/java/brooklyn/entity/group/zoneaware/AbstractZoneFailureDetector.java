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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster.ZoneFailureDetector;
import brooklyn.location.Location;

import com.google.common.annotations.Beta;
import com.google.common.base.Ticker;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Beta
public abstract class AbstractZoneFailureDetector implements ZoneFailureDetector {

    private final ConcurrentMap<Location, ZoneHistory> zoneHistories = Maps.newConcurrentMap();
    protected final Ticker ticker;
    
    public AbstractZoneFailureDetector() {
        this(Ticker.systemTicker());
    }
    
    public AbstractZoneFailureDetector(Ticker ticker) {
        this.ticker = ticker;
    }
    
    @Override
    public void onStartupSuccess(Location loc, Entity entity) {
        getZoneHistory(loc).onSuccess(currentTimeMillis());
    }

    @Override
    public void onStartupFailure(Location loc, Entity entity, Throwable cause) {
        getZoneHistory(loc).onFailure(currentTimeMillis(), cause);
    }

    @Override
    public boolean hasFailed(Location loc) {
        ZoneHistory zoneHistory = getZoneHistory(loc);
        return doHasFailed(loc, zoneHistory);
    }

    protected long currentTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(ticker.read());
    }
    
    protected ZoneHistory getZoneHistory(Location loc) {
        ZoneHistory zoneHistory = zoneHistories.get(loc);
        if (zoneHistory == null) {
            ZoneHistory newZoneHistory = newZoneHistory(loc);
            ZoneHistory oldZoneHistory = zoneHistories.putIfAbsent(loc, newZoneHistory);
            zoneHistory = (oldZoneHistory != null) ? oldZoneHistory : newZoneHistory;
        }
        return zoneHistory;
    }

    protected ZoneHistory newZoneHistory(Location loc) {
        return new ZoneHistory();
    }

    /**
     * Warn: called should normally synchronize on zoneHistory while accessing it.
     */
    protected abstract boolean doHasFailed(Location loc, ZoneHistory zoneHistory);

    /**
     * Note: callers please don't side-effect the success/failures/causes fields directly!
     * Instead consider sub-classing ZoneHistory, and overriding {@link AbstractZoneFailureDetector#newZoneHistory(Location)}.
     */
    public static class ZoneHistory {
        public final List<Long> successes = Lists.newLinkedList();
        public final List<Long> failures = Lists.newLinkedList();
        public final List<Throwable> causes = Lists.newLinkedList();
        
        public synchronized void onSuccess(long date) {
            successes.add(date);
        }

        public synchronized void onFailure(long date, Throwable cause) {
            failures.add(date);
            causes.add(cause);
        }

        public synchronized void trimOlderThan(long date) {
            assert failures.size() == causes.size() : failures.size()+" failures, but "+causes.size()+" causes; bad synchronization by callers";

            for (Iterator<Long> iter = successes.iterator(); iter.hasNext();) {
                Long d = iter.next();
                if (d < date) iter.remove();
            }
            for (Iterator<Long> iter = failures.iterator(); iter.hasNext();) {
                Iterator<Throwable> causeIter = causes.iterator();
                Long d = iter.next();
                causeIter.next();
                if (d < date) {
                    iter.remove();
                    causeIter.remove();
                } else {
                    break;
                }
            }
        }
    }
}
