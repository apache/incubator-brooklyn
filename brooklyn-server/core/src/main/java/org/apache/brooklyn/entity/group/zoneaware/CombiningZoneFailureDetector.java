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
package org.apache.brooklyn.entity.group.zoneaware;

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.entity.group.DynamicCluster.ZoneFailureDetector;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CombiningZoneFailureDetector implements ZoneFailureDetector {

    public static CombiningZoneFailureDetector failIfAny(ZoneFailureDetector... detectors) {
        Predicate<List<Boolean>> joiner = new Predicate<List<Boolean>>() {
            @Override public boolean apply(List<Boolean> input) {
                return input.contains(Boolean.TRUE);
            }
        };
        return new CombiningZoneFailureDetector(joiner, detectors);
    }

    public static CombiningZoneFailureDetector failIfAll(ZoneFailureDetector... detectors) {
        Predicate<List<Boolean>> joiner = new Predicate<List<Boolean>>() {
            @Override public boolean apply(List<Boolean> input) {
                return input.contains(Boolean.TRUE) && !input.contains(Boolean.FALSE) && !input.contains(null);
            }
        };
        return new CombiningZoneFailureDetector(joiner, detectors);
    }

    private final Predicate<? super List<Boolean>> joiner;
    private final List<ZoneFailureDetector> detectors;

    protected CombiningZoneFailureDetector(Predicate<? super List<Boolean>> joiner, ZoneFailureDetector... detectors) {
        this.joiner = joiner;
        this.detectors = ImmutableList.copyOf(detectors);
    }
    
    @Override
    public void onStartupSuccess(Location loc, Entity entity) {
        for (ZoneFailureDetector detector : detectors) {
            detector.onStartupSuccess(loc, entity);
        }
    }

    @Override
    public void onStartupFailure(Location loc, Entity entity, Throwable cause) {
        for (ZoneFailureDetector detector : detectors) {
            detector.onStartupFailure(loc, entity, cause);
        }
    }

    @Override
    public boolean hasFailed(Location loc) {
        List<Boolean> opinions = Lists.newArrayList();
        for (ZoneFailureDetector detector : detectors) {
            detector.hasFailed(loc);
        }
        return joiner.apply(opinions);
    }
}
