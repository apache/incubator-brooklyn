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
package brooklyn.policy.loadbalancing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

/**
 * Standard implementation of {@link BalanceablePoolModel}, providing essential arithmetic for item and container
 * workrates and thresholds. See subclasses for specific requirements for migrating items.
 */
public class DefaultBalanceablePoolModel<ContainerType, ItemType> implements BalanceablePoolModel<ContainerType, ItemType> {
    
    private static final Logger LOG = LoggerFactory.getLogger(DefaultBalanceablePoolModel.class);
    
    /*
     * Performance comments.
     *  - Used hprof with LoadBalancingPolicySoakTest.testLoadBalancingManyManyItemsTest (1000 items)
     *  - Prior to adding containerToItems, it created a new set by iterating over all items.
     *    This was the biggest percentage of any brooklyn code.
     *    Hence it's worth duplicating the values, keyed by item and keyed by container.
     *  - Unfortunately changing threading model (so have a "rebalancer" thread, and a thread that 
     *    processes events to update the model), get ConcurrentModificationException if don't take
     *    copy of containerToItems.get(node)...
     */
    
    // Concurrent maps cannot have null value; use this to represent when no container is supplied for an item 
    private static final String NULL_CONTAINER = "null-container";
    
    private final String name;
    private final Set<ContainerType> containers = Collections.newSetFromMap(new ConcurrentHashMap<ContainerType,Boolean>());
    private final Map<ContainerType, Double> containerToLowThreshold = new ConcurrentHashMap<ContainerType, Double>();
    private final Map<ContainerType, Double> containerToHighThreshold = new ConcurrentHashMap<ContainerType, Double>();
    private final Map<ItemType, ContainerType> itemToContainer = new ConcurrentHashMap<ItemType, ContainerType>();
    private final SetMultimap<ContainerType, ItemType> containerToItems =  Multimaps.synchronizedSetMultimap(HashMultimap.<ContainerType, ItemType>create());
    private final Map<ItemType, Double> itemToWorkrate = new ConcurrentHashMap<ItemType, Double>();
    private final Set<ItemType> immovableItems = Collections.newSetFromMap(new ConcurrentHashMap<ItemType, Boolean>());
    
    private volatile double poolLowThreshold = 0;
    private volatile double poolHighThreshold = 0;
    private volatile double currentPoolWorkrate = 0;
    
    public DefaultBalanceablePoolModel(String name) {
        this.name = name;
    }
    
    public ContainerType getParentContainer(ItemType item) {
        ContainerType result = itemToContainer.get(item);
        return (result != NULL_CONTAINER) ? result : null;
    }
    
    public Set<ItemType> getItemsForContainer(ContainerType node) {
        Set<ItemType> result = containerToItems.get(node);
        synchronized (containerToItems) {
            return (result != null) ? ImmutableSet.copyOf(result) : Collections.<ItemType>emptySet();
        }
    }
    
    public Double getItemWorkrate(ItemType item) {
        return itemToWorkrate.get(item);
    }
    
    @Override public double getPoolLowThreshold() { return poolLowThreshold; }
    @Override public double getPoolHighThreshold() { return poolHighThreshold; }
    @Override public double getCurrentPoolWorkrate() { return currentPoolWorkrate; }
    @Override public boolean isHot() { return !containers.isEmpty() && currentPoolWorkrate > poolHighThreshold; }
    @Override public boolean isCold() { return !containers.isEmpty() && currentPoolWorkrate < poolLowThreshold; }
    
    
    // Provider methods.
    
    @Override public String getName() { return name; }
    @Override public int getPoolSize() { return containers.size(); }
    @Override public Set<ContainerType> getPoolContents() { return containers; }
    @Override public String getName(ContainerType container) { return container.toString(); } // TODO: delete?
    @Override public Location getLocation(ContainerType container) { return null; } // TODO?
    
    @Override public double getLowThreshold(ContainerType container) {
        Double result = containerToLowThreshold.get(container);
        return (result != null) ? result : -1;
    }
    
    @Override public double getHighThreshold(ContainerType container) {
        Double result = containerToHighThreshold.get(container);
        return (result != null) ? result : -1;
    }
    
    @Override public double getTotalWorkrate(ContainerType container) {
        double totalWorkrate = 0;
        for (ItemType item : getItemsForContainer(container)) {
            Double workrate = itemToWorkrate.get(item);
            if (workrate != null)
                totalWorkrate += Math.abs(workrate);
        }
        return totalWorkrate;
    }
    
    @Override public Map<ContainerType, Double> getContainerWorkrates() {
        Map<ContainerType, Double> result = new LinkedHashMap<ContainerType, Double>();
        for (ContainerType node : containers)
            result.put(node, getTotalWorkrate(node));
        return result;
    }
    
    @Override public Map<ItemType, Double> getItemWorkrates(ContainerType node) {
        Map<ItemType, Double> result = new LinkedHashMap<ItemType, Double>();
        for (ItemType item : getItemsForContainer(node))
            result.put(item, itemToWorkrate.get(item));
        return result;
    }
    
    @Override public boolean isItemMoveable(ItemType item) {
        // If don't know about item, then assume not movable; otherwise has this item been explicitly flagged as immovable?
        return itemToContainer.containsKey(item) && !immovableItems.contains(item);
    }
    
    @Override public boolean isItemAllowedIn(ItemType item, Location location) {
        return true; // TODO?
    }
    
    
    // Mutators.
    
    @Override
    public void onItemMoved(ItemType item, ContainerType newNode) {
        if (!itemToContainer.containsKey(item)) {
            // Item may have been deleted; order of events received from different sources 
            // (i.e. item itself and for itemGroup membership) is non-deterministic.
            LOG.info("Balanceable pool model ignoring onItemMoved for unknown item {} to container {}; " +
            		"if onItemAdded subsequently received will get new container then", item, newNode);
            return;
        }
        ContainerType newNodeNonNull = toNonNullContainer(newNode);
        ContainerType oldNode = itemToContainer.put(item, newNodeNonNull);
        if (oldNode != null && oldNode != NULL_CONTAINER) containerToItems.remove(oldNode, item);
        if (newNode != null) containerToItems.put(newNode, item);
    }
    
    @Override
    public void onContainerAdded(ContainerType newContainer, double lowThreshold, double highThreshold) {
        boolean added = containers.add(newContainer);
        if (!added) {
            // See LoadBalancingPolicy.onContainerAdded for possible explanation of why can get duplicate calls
            LOG.debug("Duplicate container-added event for {}; ignoring", newContainer);
            return;
        }
        containerToLowThreshold.put(newContainer, lowThreshold);
        containerToHighThreshold.put(newContainer, highThreshold);
        poolLowThreshold += lowThreshold;
        poolHighThreshold += highThreshold;
    }
    
    @Override
    public void onContainerRemoved(ContainerType oldContainer) {
        containers.remove(oldContainer);
        Double containerLowThreshold = containerToLowThreshold.remove(oldContainer);
        Double containerHighThresold = containerToHighThreshold.remove(oldContainer);
        poolLowThreshold -= (containerLowThreshold != null ? containerLowThreshold : 0);
        poolHighThreshold -= (containerHighThresold != null ? containerHighThresold : 0);
        
        // TODO: assert no orphaned items
    }
    
    @Override
    public void onItemAdded(ItemType item, ContainerType parentContainer) {
        onItemAdded(item, parentContainer, false);
    }
    
    @Override
    public void onItemAdded(ItemType item, ContainerType parentContainer, boolean immovable) {
        // Duplicate calls to onItemAdded do no harm, as long as most recent is most accurate!
        // Important that it stays that way for now - See LoadBalancingPolicy.onContainerAdded for explanation.

        if (immovable)
            immovableItems.add(item);
        
        ContainerType parentContainerNonNull = toNonNullContainer(parentContainer);
        ContainerType oldNode = itemToContainer.put(item, parentContainerNonNull);
        if (oldNode != null && oldNode != NULL_CONTAINER) containerToItems.remove(oldNode, item);
        if (parentContainer != null) containerToItems.put(parentContainer, item);
    }
    
    @Override
    public void onItemRemoved(ItemType item) {
        ContainerType oldNode = itemToContainer.remove(item);
        if (oldNode != null && oldNode != NULL_CONTAINER) containerToItems.remove(oldNode, item);
        Double workrate = itemToWorkrate.remove(item);
        if (workrate != null)
            currentPoolWorkrate -= workrate;
        immovableItems.remove(item);
    }
    
    @Override
    public void onItemWorkrateUpdated(ItemType item, double newValue) {
        if (hasItem(item)) {
            Double oldValue = itemToWorkrate.put(item, newValue);
            double delta = ( newValue - (oldValue != null ? oldValue : 0) );
            currentPoolWorkrate += delta;
        } else {
            // Can happen when item removed - get notification of removal and workrate from group and item
            // respectively, so can overtake each other
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring setting of workrate for unknown item {}, to {}", item, newValue);
        }
    }
    
    private boolean hasItem(ItemType item) {
        return itemToContainer.containsKey(item);
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
        for (ContainerType container : getPoolContents()) {
            out.println("Container '"+container+"': ");
            for (ItemType item : getItemsForContainer(container)) {
                Double workrate = getItemWorkrate(item);
                out.println("\t"+"Item '"+item+"' ("+workrate+")");
            }
        }
        out.flush();
    }
    
    @SuppressWarnings("unchecked")
    private ContainerType nullContainer() {
        return (ContainerType) NULL_CONTAINER; // relies on erasure
    }
    
    private ContainerType toNonNullContainer(ContainerType container) {
        return (container != null) ? container : nullContainer();
    }
    
}
