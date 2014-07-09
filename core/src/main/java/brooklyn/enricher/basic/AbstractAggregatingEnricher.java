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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;


/**
 * AggregatingEnrichers implicitly subscribes to the same sensor<S> on all entities inside an
 * {@link Group} and should emit an aggregate<T> on the target sensor
 * 
 * @deprecated since 0.7.0; use {@link Enrichers.builder()}
 * @see Aggregator if need to sub-class
 */
public abstract class AbstractAggregatingEnricher<S,T> extends AbstractEnricher implements SensorEventListener<S> {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAggregatingEnricher.class);
    
    AttributeSensor<? extends S> source;
    protected AttributeSensor<T> target;
    protected S defaultValue;

    Set<Entity> producers;
    List<Entity> hardCodedProducers;
    boolean allMembers;
    Predicate<Entity> filter;
    
    /**
     * Users of values should either on it synchronize when iterating over its entries or use
     * copyOfValues to obtain an immutable copy of the map.
     */
    // We use a synchronizedMap over a ConcurrentHashMap for entities that store null values.
    protected final Map<Entity, S> values = Collections.synchronizedMap(new LinkedHashMap<Entity, S>());

    public AbstractAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target) {
        this(flags, source, target, null);
    }
    
    @SuppressWarnings("unchecked")
    public AbstractAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target, S defaultValue) {
        super(flags);
        this.source = source;
        this.target = target;
        this.defaultValue = defaultValue;
        hardCodedProducers = (List<Entity>) (flags.containsKey("producers") ? flags.get("producers") : Collections.emptyList());
        allMembers = (Boolean) (flags.containsKey("allMembers") ? flags.get("allMembers") : false);
        filter = flags.containsKey("filter") ? GroovyJavaMethods.<Entity>castToPredicate(flags.get("filter")) : Predicates.<Entity>alwaysTrue();
    }

    public void addProducer(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug("{} linked ({}, {}) to {}", new Object[] {this, producer, source, target});
        subscribe(producer, source, this);
        synchronized (values) {
            S vo = values.get(producer);
            if (vo==null) {
                S initialVal = ((EntityLocal)producer).getAttribute(source);
                values.put(producer, initialVal != null ? initialVal : defaultValue);
                //we might skip in onEvent in the short window while !values.containsKey(producer)
                //but that's okay because the put which would have been done there is done here now
            } else {
                //vo will be null unless some weird race with addProducer+removeProducer is occuring
                //(and that's something we can tolerate i think)
                if (LOG.isDebugEnabled()) LOG.debug("{} already had value ({}) for producer ({}); but that producer has just been added", new Object[] {this, vo, producer});
            }
        }
        onUpdated();
    }
    
    // TODO If producer removed but then get (queued) event from it after this method returns,  
    public S removeProducer(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug("{} unlinked ({}, {}) from {}", new Object[] {this, producer, source, target});
        unsubscribe(producer);
        S removed = values.remove(producer);
        onUpdated();
        return removed;
    }
    
    @Override
    public void onEvent(SensorEvent<S> event) {
        Entity e = event.getSource();
        synchronized (values) {
            if (values.containsKey(e)) {
                values.put(e, event.getValue());
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("{} received event for unknown producer ({}); presumably that producer has recently been removed", this, e);
            }
        }
        onUpdated();
    }

    /**
     * Called whenever the values for the set of producers changes (e.g. on an event, or on a member added/removed).
     * Defaults to no-op
     */
    // TODO should this be abstract?
    protected void onUpdated() {
        // no-op
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        
        for (Entity producer : hardCodedProducers) {
            if (filter.apply(producer)) {
                addProducer(producer);
            }
        }
        
        if (allMembers) {
            subscribe(entity, Changeable.MEMBER_ADDED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> it) {
                    if (filter.apply(it.getValue())) addProducer(it.getValue());
                }
            });
            subscribe(entity, Changeable.MEMBER_REMOVED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> it) {
                    removeProducer(it.getValue());
                }
            });
            
            if (entity instanceof Group) {
                for (Entity member : ((Group)entity).getMembers()) {
                    if (filter.apply(member)) {
                        addProducer(member);
                    }
                }
            }
        }
    }
    
    protected Map<Entity, S> copyOfValues() {
        synchronized (values) {
            return ImmutableMap.copyOf(values);
        }
    }
    
}
