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
package org.apache.brooklyn.policy.followthesun;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.policy.loadbalancing.Movable;

public class FollowTheSunPoolImpl extends AbstractEntity implements FollowTheSunPool {

    // FIXME Remove duplication from BalanceableWorkerPool?

    // FIXME Asymmetry between loadbalancing and followTheSun: ITEM_ADDED and ITEM_REMOVED in loadbalancing
    // are of type ContainerItemPair, but in followTheSun it is just the `Entity item`.
    
    private static final Logger LOG = LoggerFactory.getLogger(FollowTheSunPool.class);

    private Group containerGroup;
    private Group itemGroup;

    private final Set<Entity> containers = Collections.synchronizedSet(new HashSet<Entity>());
    private final Set<Entity> items = Collections.synchronizedSet(new HashSet<Entity>());

    private final SensorEventListener<Object> eventHandler = new SensorEventListener<Object>() {
        @Override
        public void onEvent(SensorEvent<Object> event) {
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", FollowTheSunPoolImpl.this, event);
            Entity source = event.getSource();
            Object value = event.getValue();
            Sensor<?> sensor = event.getSensor();

            if (sensor.equals(AbstractGroup.MEMBER_ADDED)) {
                if (source.equals(containerGroup)) {
                    onContainerAdded((Entity) value);
                } else if (source.equals(itemGroup)) {
                    onItemAdded((Entity)value);
                } else {
                    throw new IllegalStateException("unexpected event source="+source);
                }
            } else if (sensor.equals(AbstractGroup.MEMBER_REMOVED)) {
                if (source.equals(containerGroup)) {
                    onContainerRemoved((Entity) value);
                } else if (source.equals(itemGroup)) {
                    onItemRemoved((Entity) value);
                } else {
                    throw new IllegalStateException("unexpected event source="+source);
                }
            } else if (sensor.equals(Startable.SERVICE_UP)) {
                // TODO What if start has failed? Is there a sensor to indicate that?
                if ((Boolean)value) {
                    onContainerUp(source);
                } else {
                    onContainerDown(source);
                }
            } else if (sensor.equals(Movable.CONTAINER)) {
                onItemMoved(source, (Entity) value);
            } else {
                throw new IllegalStateException("Unhandled event type "+sensor+": "+event);
            }
        }
    };

    public FollowTheSunPoolImpl() {
    }

    @Override
    public void setContents(Group containerGroup, Group itemGroup) {
        this.containerGroup = containerGroup;
        this.itemGroup = itemGroup;
        subscriptions().subscribe(containerGroup, AbstractGroup.MEMBER_ADDED, eventHandler);
        subscriptions().subscribe(containerGroup, AbstractGroup.MEMBER_REMOVED, eventHandler);
        subscriptions().subscribe(itemGroup, AbstractGroup.MEMBER_ADDED, eventHandler);
        subscriptions().subscribe(itemGroup, AbstractGroup.MEMBER_REMOVED, eventHandler);

        // Process extant containers and items
        for (Entity existingContainer : containerGroup.getMembers()) {
            onContainerAdded(existingContainer);
        }
        for (Entity existingItem : itemGroup.getMembers()) {
            onItemAdded((Entity)existingItem);
        }
    }

    @Override
    public Group getContainerGroup() {
        return containerGroup;
    }

    @Override
    public Group getItemGroup() {
        return itemGroup;
    }

    @Override
    public Integer getCurrentSize() {
        return containerGroup.getCurrentSize();
    }

    @Override
    public Integer resize(Integer desiredSize) {
        if (containerGroup instanceof Resizable) return ((Resizable) containerGroup).resize(desiredSize);

        throw new UnsupportedOperationException("Container group is not resizable");
    }


    private void onContainerAdded(Entity newContainer) {
        subscriptions().subscribe(newContainer, Startable.SERVICE_UP, eventHandler);
        if (!(newContainer instanceof Startable) || Boolean.TRUE.equals(newContainer.getAttribute(Startable.SERVICE_UP))) {
            onContainerUp(newContainer);
        }
    }

    private void onContainerUp(Entity newContainer) {
        if (containers.add(newContainer)) {
            sensors().emit(CONTAINER_ADDED, newContainer);
        }
    }

    private void onContainerDown(Entity oldContainer) {
        if (containers.remove(oldContainer)) {
            sensors().emit(CONTAINER_REMOVED, oldContainer);
        }
    }

    private void onContainerRemoved(Entity oldContainer) {
        subscriptions().unsubscribe(oldContainer);
        onContainerDown(oldContainer);
    }

    private void onItemAdded(Entity item) {
        if (items.add(item)) {
            subscriptions().subscribe(item, Movable.CONTAINER, eventHandler);
            sensors().emit(ITEM_ADDED, item);
        }
    }

    private void onItemRemoved(Entity item) {
        if (items.remove(item)) {
            subscriptions().unsubscribe(item);
            sensors().emit(ITEM_REMOVED, item);
        }
    }

    private void onItemMoved(Entity item, Entity container) {
        sensors().emit(ITEM_MOVED, new ContainerItemPair(container, item));
    }
}
