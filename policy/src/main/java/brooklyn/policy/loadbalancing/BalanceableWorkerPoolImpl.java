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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;

/**
 * @see BalanceableWorkerPool
 */
public class BalanceableWorkerPoolImpl extends AbstractEntity implements BalanceableWorkerPool {

    // FIXME Asymmetry between loadbalancing and followTheSun: ITEM_ADDED and ITEM_REMOVED in loadbalancing
    // are of type ContainerItemPair, but in followTheSun it is just the `Entity item`.
    
    private static final Logger LOG = LoggerFactory.getLogger(BalanceableWorkerPool.class);
    
    private Group containerGroup;
    private Group itemGroup;
    private Resizable resizable;
    
    private final Set<Entity> containers = Collections.synchronizedSet(new HashSet<Entity>());
    private final Set<Entity> items = Collections.synchronizedSet(new HashSet<Entity>());
    
    private final SensorEventListener<Object> eventHandler = new SensorEventListener<Object>() {
        @Override
        public void onEvent(SensorEvent<Object> event) {
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", BalanceableWorkerPoolImpl.this, event);
            Entity source = event.getSource();
            Object value = event.getValue();
            Sensor<?> sensor = event.getSensor();
            
            if (sensor.equals(AbstractGroup.MEMBER_ADDED)) {
                if (source.equals(containerGroup)) {
                    onContainerAdded((BalanceableContainer<?>) value);
                } else if (source.equals(itemGroup)) {
                    onItemAdded((Entity)value);
                } else {
                    throw new IllegalStateException("unexpected event source="+source);
                }
            } else if (sensor.equals(AbstractGroup.MEMBER_REMOVED)) {
                if (source.equals(containerGroup)) {
                    onContainerRemoved((BalanceableContainer<?>) value);
                } else if (source.equals(itemGroup)) {
                    onItemRemoved((Entity) value);
                } else {
                    throw new IllegalStateException("unexpected event source="+source);
                }
            } else if (sensor.equals(Startable.SERVICE_UP)) {
                // TODO What if start has failed? Is there a sensor to indicate that?
                if ((Boolean)value) {
                    onContainerUp((BalanceableContainer<?>) source);
                } else {
                    onContainerDown((BalanceableContainer<?>) source);
                }
            } else if (sensor.equals(Movable.CONTAINER)) {
                onItemMoved(source, (BalanceableContainer<?>) value);
            } else {
                throw new IllegalStateException("Unhandled event type "+sensor+": "+event);
            }
        }
    };
    
    public BalanceableWorkerPoolImpl() {
    }

    @Override
    public void setResizable(Resizable resizable) {
        this.resizable = resizable;
    }
    
    @Override
    public void setContents(Group containerGroup, Group itemGroup) {
        this.containerGroup = containerGroup;
        this.itemGroup = itemGroup;
        if (resizable == null && containerGroup instanceof Resizable) resizable = (Resizable) containerGroup;
        
        subscribe(containerGroup, AbstractGroup.MEMBER_ADDED, eventHandler);
        subscribe(containerGroup, AbstractGroup.MEMBER_REMOVED, eventHandler);
        subscribe(itemGroup, AbstractGroup.MEMBER_ADDED, eventHandler);
        subscribe(itemGroup, AbstractGroup.MEMBER_REMOVED, eventHandler);
        
        // Process extant containers and items
        for (Entity existingContainer : containerGroup.getMembers()) {
            onContainerAdded((BalanceableContainer<?>)existingContainer);
        }
        for (Entity existingItem : itemGroup.getMembers()) {
            onItemAdded(existingItem);
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
        if (resizable != null) return resizable.resize(desiredSize);
        
        throw new UnsupportedOperationException("Container group is not resizable, and no resizable supplied: "+containerGroup+" of type "+(containerGroup != null ? containerGroup.getClass().getCanonicalName() : null));
    }
    
    private void onContainerAdded(BalanceableContainer<?> newContainer) {
        subscribe(newContainer, Startable.SERVICE_UP, eventHandler);
        if (!(newContainer instanceof Startable) || Boolean.TRUE.equals(newContainer.getAttribute(Startable.SERVICE_UP))) {
            onContainerUp(newContainer);
        }
    }
    
    private void onContainerUp(BalanceableContainer<?> newContainer) {
        if (containers.add(newContainer)) {
            emit(CONTAINER_ADDED, newContainer);
        }
    }
    
    private void onContainerDown(BalanceableContainer<?> oldContainer) {
        if (containers.remove(oldContainer)) {
            emit(CONTAINER_REMOVED, oldContainer);
        }
    }
    
    private void onContainerRemoved(BalanceableContainer<?> oldContainer) {
        unsubscribe(oldContainer);
        onContainerDown(oldContainer);
    }
    
    private void onItemAdded(Entity item) {
        if (items.add(item)) {
            subscribe(item, Movable.CONTAINER, eventHandler);
            emit(ITEM_ADDED, new ContainerItemPair(item.getAttribute(Movable.CONTAINER), item));
        }
    }
    
    private void onItemRemoved(Entity item) {
        if (items.remove(item)) {
            unsubscribe(item);
            emit(ITEM_REMOVED, new ContainerItemPair(null, item));
        }
    }
    
    private void onItemMoved(Entity item, BalanceableContainer<?> container) {
        emit(ITEM_MOVED, new ContainerItemPair(container, item));
    }
}
