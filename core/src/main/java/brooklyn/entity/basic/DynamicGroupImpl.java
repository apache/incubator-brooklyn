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
package brooklyn.entity.basic;

import groovy.lang.Closure;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.internal.CollectionChangeListener;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class DynamicGroupImpl extends AbstractGroupImpl implements DynamicGroup {

    private static final Logger log = LoggerFactory.getLogger(DynamicGroupImpl.class);

    protected final Object memberChangeMutex = new Object();

    private volatile MyEntitySetChangeListener setChangeListener = null;

    public DynamicGroupImpl() { }

    @Deprecated
    public DynamicGroupImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public void init() {
        super.init();
        setAttribute(RUNNING, true);
    }

    @Override
    public void setEntityFilter(Predicate<? super Entity> filter) {
        // TODO Sould this be "evenIfOwned"?
        setConfigEvenIfOwned(ENTITY_FILTER, filter);
        rescanEntities();
    }

    @Deprecated
    @Override
    public void setEntityFilter(Closure<Boolean> filter) {
        setEntityFilter(filter != null ? GroovyJavaMethods.<Entity>predicateFromClosure(filter) : null);
    }

    @Override
    public Predicate<? super Entity> entityFilter() {
        Predicate<? super Entity> entityFilter = getConfig(ENTITY_FILTER);
        if (entityFilter == null) {
            return Predicates.alwaysFalse();
        } else {
            return entityFilter;
        }
    }

    private boolean isRunning() {
        return Boolean.TRUE.equals(getAttribute(RUNNING));
    }

    @Override
    public void stop() {
        setAttribute(RUNNING, false);
        if (setChangeListener != null) {
            ((ManagementContextInternal) getManagementContext()).removeEntitySetListener(setChangeListener);
        }
    }

    @Override
    public <T> void addSubscription(Entity producer, Sensor<T> sensor, final Predicate<? super SensorEvent<? super T>> filter) {
        SensorEventListener<T> listener = new SensorEventListener<T>() {
                @Override
                public void onEvent(SensorEvent<T> event) {
                    if (filter.apply(event)) onEntityChanged(event.getSource());
                }
            };
        subscribe(producer, sensor, listener);
    }

    @Override
    public <T> void addSubscription(Entity producer, Sensor<T> sensor) {
        addSubscription(producer, sensor, Predicates.<SensorEvent<? super T>>alwaysTrue());
    }

    protected boolean acceptsEntity(Entity e) {
        return entityFilter().apply(e);
    }

    protected void onEntityAdded(Entity item) {
        synchronized (memberChangeMutex) {
            if (acceptsEntity(item)) {
                if (log.isDebugEnabled()) log.debug("{} detected item add {}", this, item);
                addMember(item);
            }
        }
    }

    protected void onEntityRemoved(Entity item) {
        synchronized (memberChangeMutex) {
            if (removeMember(item))
                if (log.isDebugEnabled()) log.debug("{} detected item removal {}", this, item);
        }
    }

    protected void onEntityChanged(Entity item) {
        synchronized (memberChangeMutex) {
            boolean accepts = acceptsEntity(item);
            boolean has = hasMember(item);
            if (has && !accepts) {
                removeMember(item);
                if (log.isDebugEnabled()) log.debug("{} detected item removal on change of {}", this, item);
            } else if (!has && accepts) {
                if (log.isDebugEnabled()) log.debug("{} detected item add on change of {}", this, item);
                addMember(item);
            }
        }
    }

    private class MyEntitySetChangeListener implements CollectionChangeListener<Entity> {
        @Override
        public void onItemAdded(Entity item) { onEntityAdded(item); }
        @Override
        public void onItemRemoved(Entity item) { onEntityRemoved(item); }
    }

    @Override
    public void onManagementBecomingMaster() {
        if (setChangeListener != null) {
            log.warn("{} becoming master twice", this);
            return;
        }
        setChangeListener = new MyEntitySetChangeListener();
        ((ManagementContextInternal) getManagementContext()).addEntitySetListener(setChangeListener);
        rescanEntities();
    }

    @Override
    public void onManagementNoLongerMaster() {
        if (setChangeListener == null) {
            log.warn("{} no longer master twice", this);
            return;
        }
        ((ManagementContextInternal) getManagementContext()).removeEntitySetListener(setChangeListener);
        setChangeListener = null;
    }

    @Override
    public void rescanEntities() {
        synchronized (memberChangeMutex) {
            if (!isRunning() || !getManagementSupport().isDeployed()) {
                if (log.isDebugEnabled()) log.debug("{} not scanning for children: stopped", this);
                return;
            }
            if (getConfig(ENTITY_FILTER) == null) {
                log.warn("{} not (yet) scanning for children: no filter defined", this, this);
                return;
            }
            if (getApplication() == null) {
                log.warn("{} not (yet) scanning for children: no application defined", this);
                return;
            }
            boolean changed = false;
            Collection<Entity> currentMembers = getMembers();
            Collection<Entity> toRemove = Sets.newLinkedHashSet(currentMembers);

            for (Entity it : Iterables.filter(getManagementContext().getEntityManager().getEntities(), entityFilter())) {
                toRemove.remove(it);
                if (!currentMembers.contains(it)) {
                    if (log.isDebugEnabled()) log.debug("{} rescan detected new item {}", this, it);
                    addMember(it);
                    changed = true;
                }
            }
            for (Entity it : toRemove) {
                if (log.isDebugEnabled()) log.debug("{} rescan detected vanished item {}", this, it);
                removeMember(it);
                changed = true;
            }
            if (changed && log.isDebugEnabled())
                log.debug("{} rescan complete, members now {}", this, getMembers());
        }
    }
}
