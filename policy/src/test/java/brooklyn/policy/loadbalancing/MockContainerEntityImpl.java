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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableList;
import brooklyn.util.time.Time;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


public class MockContainerEntityImpl extends AbstractGroupImpl implements MockContainerEntity {

    private static final Logger LOG = LoggerFactory.getLogger(MockContainerEntity.class);

    volatile boolean offloading;
    volatile boolean running;

    ReentrantLock _lock = new ReentrantLock();

    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: container {} setting {} to {}", new Object[] {this, attribute, val});
        return super.setAttribute(attribute, val);
    }

    @Override
    public void lock() {
        _lock.lock();
        if (!running) {
            _lock.unlock();
            throw new IllegalStateException("Container lock "+this+"; it is not running");
        }
    }

    @Override
    public void unlock() {
        _lock.unlock();
    }

    @Override
    public int getWorkrate() {
        int result = 0;
        for (Entity member : getMembers()) {
            Integer memberMetric = member.getAttribute(MockItemEntity.TEST_METRIC);
            result += ((memberMetric != null) ? memberMetric : 0);
        }
        return result;
    }

    @Override
    public Map<Entity, Double> getItemUsage() {
        Map<Entity, Double> result = Maps.newLinkedHashMap();
        for (Entity member : getMembers()) {
            Map<Entity, Double> memberItemUsage = member.getAttribute(MockItemEntity.ITEM_USAGE_METRIC);
            if (memberItemUsage != null) {
                for (Map.Entry<Entity, Double> entry : memberItemUsage.entrySet()) {
                    double val = (result.containsKey(entry.getKey()) ? result.get(entry.getKey()) : 0d);
                    val += ((entry.getValue() != null) ? entry.getValue() : 0);
                    result.put(entry.getKey(), val);
                }
            }
        }
        return result;
    }
    
    @Override
    public void addItem(Entity item) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: adding item {} to container {}", item, this);
        if (!running || offloading) throw new IllegalStateException("Container "+getDisplayName()+" is not running; cannot add item "+item);
        addMember(item);
        emit(BalanceableContainer.ITEM_ADDED, item);
    }

    @Override
    public void removeItem(Entity item) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: removing item {} from container {}", item, this);
        if (!running) throw new IllegalStateException("Container "+getDisplayName()+" is not running; cannot remove item "+item);
        removeMember(item);
        emit(BalanceableContainer.ITEM_REMOVED, item);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Movable> getBalanceableItems() {
        return (Set) Sets.newLinkedHashSet(getMembers());
    }

    public String toString() {
        return "MockContainer["+getDisplayName()+"]";
    }

    private long getDelay() {
        return getConfig(DELAY);
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: starting container {}", this);
        _lock.lock();
        try {
            if (getDelay() > 0) Time.sleep(getDelay());
            running = true;
            addLocations(locs);
            emit(Attributes.LOCATION_CHANGED, null);
            setAttribute(SERVICE_UP, true);
        } finally {
            _lock.unlock();
        }
    }

    @Override
    public void stop() {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: stopping container {}", this);
        _lock.lock();
        try {
            running = false;
            if (getDelay() > 0) Time.sleep(getDelay());
            setAttribute(SERVICE_UP, false);
        } finally {
            _lock.unlock();
        }
    }

    private void stopWithoutLock() {
        running = false;
        if (getDelay() > 0) Time.sleep(getDelay());
        setAttribute(SERVICE_UP, false);
    }

    public void offloadAndStop(final MockContainerEntity otherContainer) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: offloading container {} to {} (items {})", new Object[] {this, otherContainer, getBalanceableItems()});
        runWithLock(ImmutableList.of(this, otherContainer), new Runnable() {
            public void run() {
                offloading = false;
                for (Movable item : getBalanceableItems()) {
                    ((MockItemEntity)item).moveNonEffector(otherContainer);
                }
                if (LOG.isDebugEnabled()) LOG.debug("Mocks: stopping offloaded container {}", this);
                stopWithoutLock();
            }});
    }

    @Override
    public void restart() {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: restarting {}", this);
        throw new UnsupportedOperationException();
    }

    public static void runWithLock(List<MockContainerEntity> entitiesToLock, Runnable r) {
        List<MockContainerEntity> entitiesToLockCopy = MutableList.copyOf(Iterables.filter(entitiesToLock, Predicates.notNull()));
        List<MockContainerEntity> entitiesLocked = Lists.newArrayList();
        Collections.sort(entitiesToLockCopy, new Comparator<MockContainerEntity>() {
            public int compare(MockContainerEntity o1, MockContainerEntity o2) {
                return o1.getId().compareTo(o2.getId());
            }});

        try {
            for (MockContainerEntity it : entitiesToLockCopy) {
                it.lock();
                entitiesLocked.add(it);
            }

            r.run();

        } finally {
            for (MockContainerEntity it : entitiesLocked) {
                it.unlock();
            }
        }
    }
}
