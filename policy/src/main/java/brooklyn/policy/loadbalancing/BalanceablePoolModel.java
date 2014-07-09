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

import java.util.Map;
import java.util.Set;

import brooklyn.location.Location;

/**
 * Captures the state of a balanceable cluster of containers and all their constituent items, including workrates,
 * for consumption by a {@link BalancingStrategy}.
 */
public interface BalanceablePoolModel<ContainerType, ItemType> {
    
    // Attributes of the pool.
    public String getName();
    public int getPoolSize();
    public Set<ContainerType> getPoolContents();
    public double getPoolLowThreshold();
    public double getPoolHighThreshold();
    public double getCurrentPoolWorkrate();
    public boolean isHot();
    public boolean isCold();
    
    
    // Attributes of containers and items.
    public String getName(ContainerType container);
    public Location getLocation(ContainerType container);
    public double getLowThreshold(ContainerType container); // -1 for not known / invalid
    public double getHighThreshold(ContainerType container); // -1 for not known / invalid
    public double getTotalWorkrate(ContainerType container); // -1 for not known / invalid
    public Map<ContainerType, Double> getContainerWorkrates(); // contains -1 for items which are unknown
    /** contains -1 instead of actual item workrate, for items which cannot be moved */
    // @Nullable("null if the node is prevented from reporting and/or being adjusted, or has no data yet")
    public Map<ItemType, Double> getItemWorkrates(ContainerType container);
    public boolean isItemMoveable(ItemType item);
    public boolean isItemAllowedIn(ItemType item, Location location);
    
    // Mutators for keeping the model in-sync with the observed world
    public void onContainerAdded(ContainerType newContainer, double lowThreshold, double highThreshold);
    public void onContainerRemoved(ContainerType oldContainer);
    public void onItemAdded(ItemType item, ContainerType parentContainer);
    public void onItemAdded(ItemType item, ContainerType parentContainer, boolean immovable);
    public void onItemRemoved(ItemType item);
    public void onItemWorkrateUpdated(ItemType item, double newValue);
    public void onItemMoved(ItemType item, ContainerType targetContainer);
}
