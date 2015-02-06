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
package brooklyn.enricher.basic;

import static com.google.common.base.Preconditions.checkState;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

/** Abstract superclass for enrichers which aggregate from children and/or members */
@SuppressWarnings("serial")
public abstract class AbstractAggregator<T,U> extends AbstractEnricher implements SensorEventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAggregator.class);

    public static final ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer", "The entity whose children/members will be aggregated");

    public static final ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");

    // FIXME this is not just for "members" i think -Alex
    public static final ConfigKey<?> DEFAULT_MEMBER_VALUE = ConfigKeys.newConfigKey(Object.class, "enricher.defaultMemberValue");

    public static final ConfigKey<Set<? extends Entity>> FROM_HARDCODED_PRODUCERS = ConfigKeys.newConfigKey(new TypeToken<Set<? extends Entity>>() {}, "enricher.aggregating.fromHardcodedProducers");

    public static final ConfigKey<Boolean> FROM_MEMBERS = ConfigKeys.newBooleanConfigKey("enricher.aggregating.fromMembers");

    public static final ConfigKey<Boolean> FROM_CHILDREN = ConfigKeys.newBooleanConfigKey("enricher.aggregating.fromChildren");

    public static final ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = ConfigKeys.newConfigKey(new TypeToken<Predicate<? super Entity>>() {}, "enricher.aggregating.entityFilter");

    public static final ConfigKey<Predicate<?>> VALUE_FILTER = ConfigKeys.newConfigKey(new TypeToken<Predicate<?>>() {}, "enricher.aggregating.valueFilter");

    protected Entity producer;
    protected Sensor<U> targetSensor;
    protected T defaultMemberValue;
    protected Set<? extends Entity> fromHardcodedProducers;
    protected Boolean fromMembers;
    protected Boolean fromChildren;
    protected Predicate<? super Entity> entityFilter;
    protected Predicate<? super T> valueFilter;
    
    public AbstractAggregator() {}

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        setEntityLoadingConfig();

        if (fromHardcodedProducers == null && producer == null) producer = entity;
        checkState(fromHardcodedProducers != null ^ producer != null, "must specify one of %s (%s) or %s (%s)", 
                PRODUCER.getName(), producer, FROM_HARDCODED_PRODUCERS.getName(), fromHardcodedProducers);
        checkState(producer == null || Boolean.TRUE.equals(fromMembers) || Boolean.TRUE.equals(fromChildren), 
                "when specifying producer, must specify at least one of fromMembers (%s) or fromChildren (%s)", fromMembers, fromChildren);

        if (fromHardcodedProducers != null) {
            for (Entity producer : Iterables.filter(fromHardcodedProducers, entityFilter)) {
                addProducerHardcoded(producer);
            }
        }
        
        if (Boolean.TRUE.equals(fromMembers)) {
            setEntityBeforeSubscribingProducerMemberEvents(entity);
            setEntitySubscribeProducerMemberEvents();
            setEntityAfterSubscribingProducerMemberEvents();
        }
        
        if (Boolean.TRUE.equals(fromChildren)) {
            setEntityBeforeSubscribingProducerChildrenEvents();
            setEntitySubscribingProducerChildrenEvents();
            setEntityAfterSubscribingProducerChildrenEvents();
        }
        
        onUpdated();
    }

    @SuppressWarnings({ "unchecked" })
    protected void setEntityLoadingConfig() {
        this.producer = getConfig(PRODUCER);
        this.fromHardcodedProducers= getConfig(FROM_HARDCODED_PRODUCERS);
        this.defaultMemberValue = (T) getConfig(DEFAULT_MEMBER_VALUE);
        this.fromMembers = Maybe.fromNullable(getConfig(FROM_MEMBERS)).or(fromMembers);
        this.fromChildren = Maybe.fromNullable(getConfig(FROM_CHILDREN)).or(fromChildren);
        this.entityFilter = (Predicate<? super Entity>) (getConfig(ENTITY_FILTER) == null ? Predicates.alwaysTrue() : getConfig(ENTITY_FILTER));
        this.valueFilter = (Predicate<? super T>) (getConfig(VALUE_FILTER) == null ? Predicates.alwaysTrue() : getConfig(VALUE_FILTER));
        
        setEntityLoadingTargetConfig();
    }
    @SuppressWarnings({ "unchecked" })
    protected void setEntityLoadingTargetConfig() {
        this.targetSensor = (Sensor<U>) getRequiredConfig(TARGET_SENSOR);
    }

    protected void setEntityBeforeSubscribingProducerMemberEvents(EntityLocal entity) {
        checkState(producer instanceof Group, "must be a group when fromMembers true: producer=%s; entity=%s; "
                + "hardcodedProducers=%s", getConfig(PRODUCER), entity, fromHardcodedProducers);
    }

    protected void setEntitySubscribeProducerMemberEvents() {
        subscribe(producer, Changeable.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                if (entityFilter.apply(event.getValue())) {
                    addProducerMember(event.getValue());
                    onUpdated();
                }
            }
        });
        subscribe(producer, Changeable.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                removeProducer(event.getValue());
                onUpdated();
            }
        });
    }

    protected void setEntityAfterSubscribingProducerMemberEvents() {
        if (producer instanceof Group) {
            for (Entity member : Iterables.filter(((Group)producer).getMembers(), entityFilter)) {
                addProducerMember(member);
            }
        }
    }

    protected void setEntityBeforeSubscribingProducerChildrenEvents() {
    }

    protected void setEntitySubscribingProducerChildrenEvents() {
        subscribe(producer, AbstractEntity.CHILD_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                removeProducer(event.getValue());
                onUpdated();
            }
        });
        subscribe(producer, AbstractEntity.CHILD_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                if (entityFilter.apply(event.getValue())) {
                    addProducerChild(event.getValue());
                    onUpdated();
                }
            }
        });
    }

    protected void setEntityAfterSubscribingProducerChildrenEvents() {
        for (Entity child : Iterables.filter(producer.getChildren(), entityFilter)) {
            addProducerChild(child);
        }
    }

    protected abstract void addProducerHardcoded(Entity producer);
    protected abstract void addProducerMember(Entity producer);
    protected abstract void addProducerChild(Entity producer);
    
    // TODO If producer removed but then get (queued) event from it after this method returns,  
    protected void removeProducer(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug("{} stopped listening to {}", new Object[] {this, producer });
        unsubscribe(producer);
        onProducerRemoved(producer);
    }

    protected abstract void onProducerAdded(Entity producer);

    protected abstract void onProducerRemoved(Entity producer);


    /**
     * Called whenever the values for the set of producers changes (e.g. on an event, or on a member added/removed).
     */
    protected void onUpdated() {
        try {
            emit(targetSensor, compute());
        } catch (Throwable t) {
            LOG.warn("Error calculating and setting aggregate for enricher "+this, t);
            throw Exceptions.propagate(t);
        }
    }

    protected abstract Object compute();
    
}
