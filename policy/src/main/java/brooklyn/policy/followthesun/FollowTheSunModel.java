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

import java.util.Map;
import java.util.Set;

import brooklyn.location.Location;

/**
 * Captures the state of items, containers and locations for the purpose of moving items around
 * to minimise latency. For consumption by a {@link FollowTheSunStrategy}.
 */
public interface FollowTheSunModel<ContainerType, ItemType> {

    // Attributes of the pool.
    public String getName();
    
    // Attributes of containers and items.
    public String getName(ItemType item);
    public Set<ItemType> getItems();
    public Map<ItemType, Map<Location, Double>> getDirectSendsToItemByLocation();
    public Location getItemLocation(ItemType item);
    public ContainerType getItemContainer(ItemType item);
    public Location getContainerLocation(ContainerType container);
    public boolean hasActiveMigration(ItemType item);
    public Set<ContainerType> getAvailableContainersFor(ItemType item, Location location);
    public boolean isItemMoveable(ItemType item);
    public boolean isItemAllowedIn(ItemType item, Location location);
    
    // Mutators for keeping the model in-sync with the observed world
    public void onContainerAdded(ContainerType container, Location location);
    public void onContainerRemoved(ContainerType container);
    public void onContainerLocationUpdated(ContainerType container, Location location);

    public void onItemAdded(ItemType item, ContainerType parentContainer, boolean immovable);
    public void onItemRemoved(ItemType item);
    public void onItemUsageUpdated(ItemType item, Map<? extends ItemType, Double> newValues);
    public void onItemMoved(ItemType item, ContainerType newContainer);
}
