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

import java.util.Set;

/**
 * Provides conveniences for searching for hot/cold containers in a provided pool model.
 * Ported from Monterey v3, with irrelevant bits removed.
 */
public class PolicyUtilForPool<ContainerType, ItemType> {
    
    private final BalanceablePoolModel<ContainerType, ItemType> model;
    
    
    public PolicyUtilForPool (BalanceablePoolModel<ContainerType, ItemType> model) {
        this.model = model;
    }
    
    public ContainerType findColdestContainer(Set<ContainerType> excludedContainers) {
        return findColdestContainer(excludedContainers, null);
    }
    
    /**
     * Identifies the container with the maximum spare capacity (highThreshold - currentWorkrate),
     * returns null if none of the model's nodes has spare capacity.
     */
    public ContainerType findColdestContainer(Set<ContainerType> excludedContainers, LocationConstraint locationConstraint) {
        double maxSpareCapacity = 0;
        ContainerType coldest = null;
        
        for (ContainerType c : model.getPoolContents()) {
            if (excludedContainers.contains(c))
                continue;
            if (locationConstraint != null && !locationConstraint.isPermitted(model.getLocation(c)))
                continue;
            
            double highThreshold = model.getHighThreshold(c);
            double totalWorkrate = model.getTotalWorkrate(c);
            double spareCapacity = highThreshold - totalWorkrate;
            
            if (highThreshold == -1 || totalWorkrate == -1) {
                continue; // container presumably has been removed
            }
            if (spareCapacity > maxSpareCapacity) {
                maxSpareCapacity = spareCapacity;
                coldest = c;
            }
        }
        return coldest;
    }
    
    /**
     * Identifies the container with the maximum overshoot (currentWorkrate - highThreshold),
     * returns null if none of the model's  nodes has an overshoot.
     */
    public ContainerType findHottestContainer(Set<ContainerType> excludedContainers) {
        double maxOvershoot = 0;
        ContainerType hottest = null;
        
        for (ContainerType c : model.getPoolContents()) {
            if (excludedContainers.contains(c))
                continue;
            
            double totalWorkrate = model.getTotalWorkrate(c);
            double highThreshold = model.getHighThreshold(c);
            double overshoot = totalWorkrate - highThreshold;
            
            if (highThreshold == -1 || totalWorkrate == -1) {
                continue; // container presumably has been removed
            }
            if (overshoot > maxOvershoot) {
                maxOvershoot = overshoot;
                hottest = c;
            }
        }
        return hottest;
    }
    
}