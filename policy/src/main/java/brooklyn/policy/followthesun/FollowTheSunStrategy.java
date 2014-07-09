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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.loadbalancing.Movable;

import com.google.common.collect.Iterables;

// TODO: extract interface
public class FollowTheSunStrategy<ContainerType extends Entity, ItemType extends Movable> {
    
    // This is a modified version of the InterGeographyLatencyPolicy (aka Follow-The-Sun) policy from Monterey v3.
    
    // TODO location constraints
    
    private static final Logger LOG = LoggerFactory.getLogger(FollowTheSunStrategy.class);
    
    private final FollowTheSunParameters parameters;
    private final FollowTheSunModel<ContainerType,ItemType> model;
    private final String name;
    
    public FollowTheSunStrategy(FollowTheSunModel<ContainerType,ItemType> model, FollowTheSunParameters parameters) {
        this.model = model;
        this.parameters = parameters;
        this.name = model.getName();
    }
    
    public void rebalance() {
        try {
            Set<ItemType> items = model.getItems();
            Map<ItemType, Map<Location, Double>> directSendsToItemByLocation = model.getDirectSendsToItemByLocation();
            
            for (ItemType item : items) {
                String itemName = model.getName(item);
                Location activeLocation = model.getItemLocation(item);
                ContainerType activeContainer = model.getItemContainer(item);
                Map<Location, Double> sendsByLocation = directSendsToItemByLocation.get(item);
                if (sendsByLocation == null) sendsByLocation = Collections.emptyMap();
                
                if (parameters.excludedLocations.contains(activeLocation)) {
                    if (LOG.isTraceEnabled()) LOG.trace("Ignoring segment {} as it is in {}", itemName, activeLocation);
                    continue;
                }
                if (!model.isItemMoveable(item)) {
                    if (LOG.isDebugEnabled()) LOG.debug("POLICY {} skipping any migration of {}, it is not moveable", name, itemName);
                    continue;
                }
                if (model.hasActiveMigration(item)) {
                    LOG.info("POLICY {} skipping any migration of {}, it is involved in an active migration already", name, itemName);
                    continue;
                }
                
                double total = DefaultFollowTheSunModel.sum(sendsByLocation.values());

                if (LOG.isTraceEnabled()) LOG.trace("POLICY {} detected {} msgs/sec in {}, split up as: {}", new Object[] {name, total, itemName, sendsByLocation});
                
                Double current = sendsByLocation.get(activeLocation);
                if (current == null) current=0d;
                List<WeightedObject<Location>> locationsWtd = new ArrayList<WeightedObject<Location>>();
                if (total > 0) {
                    for (Map.Entry<Location, Double> entry : sendsByLocation.entrySet()) {
                        Location l = entry.getKey();
                        Double d = entry.getValue();
                        if (d > current) locationsWtd.add(new WeightedObject<Location>(l, d));
                    }
                }
                Collections.sort(locationsWtd);
                Collections.reverse(locationsWtd);
                
                double highestMsgRate = -1;
                Location highestLocation = null;
                ContainerType optimalContainerInHighest = null;
                while (!locationsWtd.isEmpty()) {
                    WeightedObject<Location> weightedObject = locationsWtd.remove(0);
                    highestMsgRate = weightedObject.getWeight();
                    highestLocation = weightedObject.getObject();
                    optimalContainerInHighest = findOptimal(model.getAvailableContainersFor(item, highestLocation));
                    if (optimalContainerInHighest != null) {
                        break;
                    }
                }
                if (optimalContainerInHighest == null) {
                    if (LOG.isDebugEnabled()) LOG.debug("POLICY {} detected {} is already in optimal permitted location ({} of {} msgs/sec)", new Object[] {name, itemName, highestMsgRate, total});
                    continue;                   
                }
                
                double nextHighestMsgRate = -1;
                ContainerType optimalContainerInNextHighest = null;
                while (!locationsWtd.isEmpty()) {
                    WeightedObject<Location> weightedObject = locationsWtd.remove(0);
                    nextHighestMsgRate = weightedObject.getWeight();
                    Location nextHighestLocation = weightedObject.getObject();
                    optimalContainerInNextHighest = findOptimal(model.getAvailableContainersFor(item, nextHighestLocation));
                    if (optimalContainerInNextHighest != null) {
                        break;
                    }
                }
                if (optimalContainerInNextHighest == null) {
                    nextHighestMsgRate = current;
                }
                
                if (parameters.isTriggered(highestMsgRate, total, nextHighestMsgRate, current)) {
                    LOG.info("POLICY "+name+" detected "+itemName+" should be in location "+highestLocation+" on "+optimalContainerInHighest+" ("+highestMsgRate+" of "+total+" msgs/sec), migrating");
                    try {
                        if (activeContainer.equals(optimalContainerInHighest)) {
                            //shouldn't happen
                            LOG.warn("POLICY "+name+" detected "+itemName+" should move to "+optimalContainerInHighest+" ("+highestMsgRate+" of "+total+" msgs/sec) but it is already there with "+current+" msgs/sec");
                        } else {
                            item.move(optimalContainerInHighest);
                            model.onItemMoved(item, optimalContainerInHighest);
                        }
                    } catch (Exception e) {
                        LOG.warn("POLICY "+name+" detected "+itemName+" should be on "+optimalContainerInHighest+", but can't move it: "+e, e);
                    }
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("POLICY "+name+" detected "+itemName+" need not move to "+optimalContainerInHighest+" ("+highestMsgRate+" of "+total+" msgs/sec not much better than "+current+" at "+activeContainer+")");
                }
            }
        } catch (Exception e) {
            LOG.warn("Error in policy "+name+" (ignoring): "+e, e);
        }
    }

    private ContainerType findOptimal(Collection<ContainerType> contenders) {
        /*
         * TODO should choose the least loaded mediator. Currently chooses first available, and relies 
         * on a load-balancer to move it again; would be good if these could share decision code so move 
         * it to the right place immediately. e.g.
         *   policyUtil.findLeastLoadedMediator(nodesInLocation);
         */
        return (contenders.isEmpty() ? null : Iterables.get(contenders, 0));
    }
}
