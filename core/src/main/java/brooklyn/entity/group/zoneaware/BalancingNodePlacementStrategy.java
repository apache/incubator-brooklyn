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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster.NodePlacementStrategy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * Default node placement strategy: attempts to keep the number of nodes balanced across the available locations.
 */
public class BalancingNodePlacementStrategy implements NodePlacementStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(BalancingNodePlacementStrategy.class);
    
    @Override
    public List<Location> locationsForAdditions(Multimap<Location, Entity> currentMembers, Collection<? extends Location> locs, int numToAdd) {
        if (locs.isEmpty() && numToAdd > 0) {
            throw new IllegalArgumentException("No locations supplied, when requesting locations for "+numToAdd+" nodes");
        }
        
        List<Location> result = Lists.newArrayList();
        Map<Location, Integer> locSizes = toMutableLocationSizes(currentMembers, locs);
        for (int i = 0; i < numToAdd; i++) {
            // TODO Inefficient to loop this many times! But not called with big numbers.
            Location leastPopulatedLoc = null;
            int leastPopulatedLocSize = 0;
            for (Location loc : locs) {
                int locSize = locSizes.get(loc);
                if (leastPopulatedLoc == null || locSize < leastPopulatedLocSize) {
                    leastPopulatedLoc = loc;
                    leastPopulatedLocSize = locSize;
                }
            }
            assert leastPopulatedLoc != null : "leastPopulatedLoc=null; locs="+locs+"; currentMembers="+currentMembers;
            result.add(leastPopulatedLoc);
            locSizes.put(leastPopulatedLoc, locSizes.get(leastPopulatedLoc)+1);
        }
        return result;
    }

    
    @Override
    public List<Entity> entitiesToRemove(Multimap<Location, Entity> currentMembers, int numToRemove) {
        if (currentMembers.isEmpty()) {
            throw new IllegalArgumentException("No members supplied, when requesting removal of "+numToRemove+" nodes");
        }
        if (currentMembers.size() < numToRemove) {
            LOG.warn("Request to remove "+numToRemove+" when only "+currentMembers.size()+" members (continuing): "+currentMembers);
            numToRemove = currentMembers.size();
        }
        Map<Location, Integer> numToRemovePerLoc = Maps.newLinkedHashMap();
        Map<Location, Integer> locSizes = toMutableLocationSizes(currentMembers, ImmutableList.<Location>of());
        for (int i = 0; i < numToRemove; i++) {
            // TODO Inefficient to loop this many times! But not called with big numbers.
            Location mostPopulatedLoc = null;
            int mostPopulatedLocSize = 0;
            for (Location loc : locSizes.keySet()) {
                int locSize = locSizes.get(loc);
                if (locSize > 0 && (mostPopulatedLoc == null || locSize > mostPopulatedLocSize)) {
                    mostPopulatedLoc = loc;
                    mostPopulatedLocSize = locSize;
                }
            }
            assert mostPopulatedLoc != null : "leastPopulatedLoc=null; currentMembers="+currentMembers;
            numToRemovePerLoc.put(mostPopulatedLoc, ((numToRemovePerLoc.get(mostPopulatedLoc) == null) ? 0 : numToRemovePerLoc.get(mostPopulatedLoc))+ 1);
            locSizes.put(mostPopulatedLoc, locSizes.get(mostPopulatedLoc)-1);
        }
        
        List<Entity> result = Lists.newArrayList();
        for (Map.Entry<Location, Integer> entry : numToRemovePerLoc.entrySet()) {
            result.addAll(pickNewest(currentMembers.get(entry.getKey()), entry.getValue()));
        }
        return result;
    }
    
    protected Map<Location,Integer> toMutableLocationSizes(Multimap<Location, Entity> currentMembers, Iterable<? extends Location> otherLocs) {
        Map<Location,Integer> result = Maps.newLinkedHashMap();
        for (Location key : currentMembers.keySet()) {
            result.put(key, currentMembers.get(key).size());
        }
        for (Location otherLoc : otherLocs) {
            if (!result.containsKey(otherLoc)) {
                result.put(otherLoc, 0);
            }
        }
        return result;
    }
    
    protected Collection<Entity> pickNewest(Collection<Entity> contenders, Integer numToPick) {
        // choose newest entity that is stoppable; sort so newest is first
        List<Entity> stoppables = Lists.newLinkedList(Iterables.filter(contenders, Predicates.instanceOf(Startable.class)));
        Collections.sort(stoppables, new Comparator<Entity>() {
            @Override public int compare(Entity a, Entity b) {
                return (int) (b.getCreationTime() - a.getCreationTime());
            }
        });
        return stoppables.subList(0, Math.min(numToPick, stoppables.size()));
    }
}