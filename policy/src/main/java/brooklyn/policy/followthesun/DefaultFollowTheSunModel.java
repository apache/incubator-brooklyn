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
package brooklyn.policy.followthesun;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class DefaultFollowTheSunModel<ContainerType, ItemType> implements FollowTheSunModel<ContainerType, ItemType> {
    
    private static final Logger LOG = LoggerFactory.getLogger(DefaultFollowTheSunModel.class);
    
    // Concurrent maps cannot have null value; use this to represent when no container is supplied for an item 
    private static final String NULL = "null-val";
    private static final Location NULL_LOCATION = new AbstractLocation(newHashMap("name","null-location")) {};
    
    private final String name;
    private final Set<ContainerType> containers = Collections.newSetFromMap(new ConcurrentHashMap<ContainerType,Boolean>());
    private final Map<ItemType, ContainerType> itemToContainer = new ConcurrentHashMap<ItemType, ContainerType>();
    private final Map<ContainerType, Location> containerToLocation = new ConcurrentHashMap<ContainerType, Location>();
    private final Map<ItemType, Location> itemToLocation = new ConcurrentHashMap<ItemType, Location>();
    private final Map<ItemType, Map<? extends ItemType, Double>> itemUsage = new ConcurrentHashMap<ItemType, Map<? extends ItemType,Double>>();
    private final Set<ItemType> immovableItems = Collections.newSetFromMap(new ConcurrentHashMap<ItemType, Boolean>());

    public DefaultFollowTheSunModel(String name) {
        this.name = name;
    }

    @Override
    public Set<ItemType> getItems() {
        return itemToContainer.keySet();
    }
    
    @Override
    public ContainerType getItemContainer(ItemType item) {
        ContainerType result = itemToContainer.get(item);
        return (isNull(result) ? null : result);
    }
    
    @Override
    public Location getItemLocation(ItemType item) {
        Location result = itemToLocation.get(item);
        return (isNull(result) ? null : result);
    }
    
    @Override
    public Location getContainerLocation(ContainerType container) {
        Location result = containerToLocation.get(container);
        return (isNull(result) ? null : result);
    }
    
    // Provider methods.
    
    @Override public String getName() {
        return name;
    }
    
    // TODO: delete?
    @Override public String getName(ItemType item) {
        return item.toString();
    }
    
    @Override public boolean isItemMoveable(ItemType item) {
        // If don't know about item, then assume not movable; otherwise has this item been explicitly flagged as immovable?
        return hasItem(item) && !immovableItems.contains(item);
    }
    
    @Override public boolean isItemAllowedIn(ItemType item, Location location) {
        return true; // TODO?
    }
    
    @Override public boolean hasActiveMigration(ItemType item) {
        return false; // TODO?
    }
    
    @Override
    // FIXME Too expensive to compute; store in a different data structure?
    public Map<ItemType, Map<Location, Double>> getDirectSendsToItemByLocation() {
        Map<ItemType, Map<Location, Double>> result = new LinkedHashMap<ItemType, Map<Location,Double>>(getNumItems());
        
        for (Map.Entry<ItemType, Map<? extends ItemType, Double>> entry : itemUsage.entrySet()) {
            ItemType targetItem = entry.getKey();
            Map<? extends ItemType, Double> sources = entry.getValue();
            if (sources.isEmpty()) continue; // no-one talking to us
            
            Map<Location, Double> targetUsageByLocation = new LinkedHashMap<Location, Double>();
            result.put(targetItem, targetUsageByLocation);

            for (Map.Entry<? extends ItemType, Double> entry2 : sources.entrySet()) {
                ItemType sourceItem = entry2.getKey();
                Location sourceLocation = getItemLocation(sourceItem);
                double usageVal = (entry.getValue() != null) ? entry2.getValue() : 0d;
                if (sourceLocation == null) continue; // don't know where to attribute this load; e.g. item may have just terminated
                if (sourceItem.equals(targetItem)) continue; // ignore msgs to self
                
                Double usageValTotal = targetUsageByLocation.get(sourceLocation);
                double newUsageValTotal = (usageValTotal != null ? usageValTotal : 0d) + usageVal;
                targetUsageByLocation.put(sourceLocation, newUsageValTotal);
            }
        }
        
        return result;
    }
    
    @Override
    public Set<ContainerType> getAvailableContainersFor(ItemType item, Location location) {
        checkNotNull(location);
        return getContainersInLocation(location);
    }


    // Mutators.
    
    @Override
    public void onItemMoved(ItemType item, ContainerType newContainer) {
        // idempotent, as may be called multiple times
        Location newLocation = (newContainer != null) ? containerToLocation.get(newContainer) : null;
        ContainerType newContainerNonNull = toNonNullContainer(newContainer);
        Location newLocationNonNull = toNonNullLocation(newLocation);
        ContainerType oldContainer = itemToContainer.put(item, newContainerNonNull);
        Location oldLocation = itemToLocation.put(item, newLocationNonNull);
    }
    
    @Override
    public void onContainerAdded(ContainerType container, Location location) {
        Location locationNonNull = toNonNullLocation(location);
        containers.add(container);
        containerToLocation.put(container, locationNonNull);
        for (ItemType item : getItemsOnContainer(container)) {
            itemToLocation.put(item, locationNonNull);
        }
    }
    
    @Override
    public void onContainerRemoved(ContainerType container) {
        containers.remove(container);
        containerToLocation.remove(container);
    }
    
    public void onContainerLocationUpdated(ContainerType container, Location location) {
        if (!containers.contains(container)) {
            // unknown container; probably just stopped? 
            // If this overtook onContainerAdded, then assume we'll lookup the location and get it right in onContainerAdded
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring setting of location for unknown container {}, to {}", container, location);
            return;
        }
        Location locationNonNull = toNonNullLocation(location);
        containerToLocation.put(container, locationNonNull);
        for (ItemType item : getItemsOnContainer(container)) {
            itemToLocation.put(item, locationNonNull);
        }
    }

    @Override
    public void onItemAdded(ItemType item, ContainerType container, boolean immovable) {
        // idempotent, as may be called multiple times
        
        if (immovable) {
            immovableItems.add(item);
        }
        Location location = (container != null) ? containerToLocation.get(container) : null;
        ContainerType containerNonNull = toNonNullContainer(container);
        Location locationNonNull = toNonNullLocation(location);
        ContainerType oldContainer = itemToContainer.put(item, containerNonNull);
        Location oldLocation = itemToLocation.put(item, locationNonNull);
    }
    
    @Override
    public void onItemRemoved(ItemType item) {
        itemToContainer.remove(item);
        itemToLocation.remove(item);
        itemUsage.remove(item);
        immovableItems.remove(item);
    }
    
    @Override
    public void onItemUsageUpdated(ItemType item, Map<? extends ItemType, Double> newValue) {
        if (hasItem(item)) {
            itemUsage.put(item, newValue);
        } else {
            // Can happen when item removed - get notification of removal and workrate from group and item
            // respectively, so can overtake each other
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring setting of usage for unknown item {}, to {}", item, newValue);
        }
    }
    
    
    // Additional methods for tests.

    /**
     * Warning: this can be an expensive (time and memory) operation if there are a lot of items/containers. 
     */
    @VisibleForTesting
    public String itemDistributionToString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        dumpItemDistribution(new PrintStream(baos));
        return new String(baos.toByteArray());
    }

    @VisibleForTesting
    public void dumpItemDistribution() {
        dumpItemDistribution(System.out);
    }
    
    @VisibleForTesting
    public void dumpItemDistribution(PrintStream out) {
        Map<ItemType, Map<Location, Double>> directSendsToItemByLocation = getDirectSendsToItemByLocation();
        
        out.println("Follow-The-Sun dump: ");
        for (Location location: getLocations()) {
            out.println("\t"+"Location "+location);
            for (ContainerType container : getContainersInLocation(location)) {
                out.println("\t\t"+"Container "+container);
                for (ItemType item : getItemsOnContainer(container)) {
                    Map<Location, Double> inboundUsage = directSendsToItemByLocation.get(item);
                    Map<? extends ItemType, Double> outboundUsage = itemUsage.get(item);
                    double totalInboundByLocation = (inboundUsage != null) ? sum(inboundUsage.values()) : 0d;
                    double totalInboundByActor = (outboundUsage != null) ? sum(outboundUsage.values()) : 0d;
                    out.println("\t\t\t"+"Item "+item);
                    out.println("\t\t\t\t"+"Inbound-by-location: "+totalInboundByLocation+": "+inboundUsage);
                    out.println("\t\t\t\t"+"Inbound-by-actor: "+totalInboundByActor+": "+outboundUsage);
                }
            }
        }
        out.flush();
    }
    
    private boolean hasItem(ItemType item) {
        return itemToContainer.containsKey(item);
    }
    
    private Set<Location> getLocations() {
        return ImmutableSet.copyOf(containerToLocation.values());
    }
    
    private Set<ContainerType> getContainersInLocation(Location location) {
        Set<ContainerType> result = new LinkedHashSet<ContainerType>();
        for (Map.Entry<ContainerType, Location> entry : containerToLocation.entrySet()) {
            if (location.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
    
    private Set<ItemType> getItemsOnContainer(ContainerType container) {
        Set<ItemType> result = new LinkedHashSet<ItemType>();
        for (Map.Entry<ItemType, ContainerType> entry : itemToContainer.entrySet()) {
            if (container.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
    
    private int getNumItems() {
        return itemToContainer.size();
    }
    
    @SuppressWarnings("unchecked")
    private ContainerType nullContainer() {
        return (ContainerType) NULL; // relies on erasure
    }
    
    private Location nullLocation() {
        return NULL_LOCATION;
    }
    
    private ContainerType toNonNullContainer(ContainerType val) {
        return (val != null) ? val : nullContainer();
    }
    
    private Location toNonNullLocation(Location val) {
        return (val != null) ? val : nullLocation();
    }
    
    private boolean isNull(Object val) {
        return val == NULL || val == NULL_LOCATION;
    }
    
    // TODO Move to utils; or stop AbstractLocation from removing things from the map!
    public static <K,V> Map<K,V> newHashMap(K k, V v) {
        Map<K,V> result = Maps.newLinkedHashMap();
        result.put(k, v);
        return result;
    }
    
    public static double sum(Collection<? extends Number> values) {
        double total = 0;
        for (Number d : values) {
            total += d.doubleValue();
        }
        return total;
    }
}
