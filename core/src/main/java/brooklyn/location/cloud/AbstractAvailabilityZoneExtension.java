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
package brooklyn.location.cloud;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import brooklyn.location.Location;
import brooklyn.management.ManagementContext;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@Beta
public abstract class AbstractAvailabilityZoneExtension implements AvailabilityZoneExtension {

    protected final ManagementContext managementContext;
    protected final AtomicReference<List<Location>> subLocations = new AtomicReference<List<Location>>();
    private final Object mutex = new Object();
    
    public AbstractAvailabilityZoneExtension(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public List<Location> getSubLocations(int max) {
        List<Location> all = getAllSubLocations();
        return all.subList(0, Math.min(max, all.size()));
    }

    @Override
    public List<Location> getSubLocationsByName(Predicate<? super String> namePredicate, int max) {
        List<Location> result = Lists.newArrayList();
        List<Location> all = getAllSubLocations();
        for (Location loc : all) {
            if (isNameMatch(loc, namePredicate)) {
                result.add(loc);
            }
        }
        return Collections.<Location>unmodifiableList(result);
    }

    @Override
    public List<Location> getAllSubLocations() {
        synchronized (mutex) {
            if (subLocations.get() == null) {
                List<Location> result = doGetAllSubLocations();
                subLocations.set(ImmutableList.copyOf(result));
            }
        }
        return subLocations.get();
    }

    /**
     * <strong>Note</strong> this method can be called while synchronized on {@link #mutex}.
     */
    // TODO bad pattern, as this will likely call alien code (such as asking cloud provider?!)
    protected abstract List<Location> doGetAllSubLocations();

    protected abstract boolean isNameMatch(Location loc, Predicate<? super String> namePredicate);
}
