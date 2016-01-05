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
package org.apache.brooklyn.core.sensor;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.BrooklynLogging;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A {@link Map} of {@link Entity} attribute values.
 */
public final class AttributeMap {

    static final Logger log = LoggerFactory.getLogger(AttributeMap.class);

    private static enum Marker {
        NULL;
    }
    
    private final AbstractEntity entity;

    // Assumed to be something like a ConcurrentMap passed in.
    private final Map<Collection<String>, Object> values;

    /**
     * Creates a new AttributeMap.
     *
     * @param entity the EntityLocal this AttributeMap belongs to.
     * @throws NullPointerException if entity is null
     */
    public AttributeMap(AbstractEntity entity) {
        // Not using ConcurrentMap, because want to (continue to) allow null values.
        // Could use ConcurrentMapAcceptingNullVals (with the associated performance hit on entrySet() etc).
        this(entity, Collections.synchronizedMap(Maps.<Collection<String>, Object>newLinkedHashMap()));
    }

    /**
     * Creates a new AttributeMap.
     *
     * @param entity  the EntityLocal this AttributeMap belongs to.
     * @param storage the Map in which to store the values - should be concurrent or synchronized.
     * @throws NullPointerException if entity is null
     */
    public AttributeMap(AbstractEntity entity, Map<Collection<String>, Object> storage) {
        this.entity = checkNotNull(entity, "entity must be specified");
        this.values = checkNotNull(storage, "storage map must not be null");
    }

    public Map<Collection<String>, Object> asRawMap() {
        synchronized (values) {
            return ImmutableMap.copyOf(values);
        }
    }

    public Map<String, Object> asMap() {
        Map<String, Object> result = Maps.newLinkedHashMap();
        synchronized (values) {
            for (Map.Entry<Collection<String>, Object> entry : values.entrySet()) {
                String sensorName = Joiner.on('.').join(entry.getKey());
                Object val = (isNull(entry.getValue())) ? null : entry.getValue();
                result.put(sensorName, val);
            }
        }
        return result;
    }
    
    /**
     * Updates the value.
     *
     * @param path the path to the value.
     * @param newValue the new value
     * @return the old value.
     * @throws IllegalArgumentException if path is null or empty
     */
    // TODO path must be ordered(and legal to contain duplicates like "a.b.a"; list would be better
    public <T> T update(Collection<String> path, T newValue) {
        checkPath(path);

        if (newValue == null) {
            newValue = typedNull();
        }

        if (log.isTraceEnabled()) {
            log.trace("setting sensor {}={} for {}", new Object[] {path, newValue, entity});
        }

        @SuppressWarnings("unchecked")
        T oldValue = (T) values.put(path, newValue);
        return (isNull(oldValue)) ? null : oldValue;
    }

    private void checkPath(Collection<String> path) {
        Preconditions.checkNotNull(path, "path can't be null");
        Preconditions.checkArgument(!path.isEmpty(), "path can't be empty");
    }

    public <T> T update(AttributeSensor<T> attribute, T newValue) {
        T oldValue = updateWithoutPublishing(attribute, newValue);
        entity.emitInternal(attribute, newValue);
        return oldValue;
    }
    
    public <T> T updateWithoutPublishing(AttributeSensor<T> attribute, T newValue) {
        if (log.isTraceEnabled()) {
            Object oldValue = getValue(attribute);
            if (!Objects.equal(oldValue, newValue != null)) {
                log.trace("setting attribute {} to {} (was {}) on {}", new Object[] {attribute.getName(), newValue, oldValue, entity});
            } else {
                log.trace("setting attribute {} to {} (unchanged) on {}", new Object[] {attribute.getName(), newValue, this});
            }
        }

        T oldValue = (T) update(attribute.getNameParts(), newValue);
        
        return (isNull(oldValue)) ? null : oldValue;
    }

    /**
     * Where atomicity is desired, the methods in this class synchronize on the {@link #values} map.
     */
    public <T> T modify(AttributeSensor<T> attribute, Function<? super T, Maybe<T>> modifier) {
        synchronized (values) {
            T oldValue = getValue(attribute);
            Maybe<? extends T> newValue = modifier.apply(oldValue);

            if (newValue.isPresent()) {
                if (log.isTraceEnabled()) log.trace("modified attribute {} to {} (was {}) on {}", new Object[] {attribute.getName(), newValue, oldValue, entity});
                return update(attribute, newValue.get());
            } else {
                if (log.isTraceEnabled()) log.trace("modified attribute {} unchanged; not emitting on {}", new Object[] {attribute.getName(), newValue, this});
                return oldValue;
            }
        }
    }

    public void remove(AttributeSensor<?> attribute) {
        BrooklynLogging.log(log, BrooklynLogging.levelDebugOrTraceIfReadOnly(entity),
            "removing attribute {} on {}", attribute.getName(), entity);

        remove(attribute.getNameParts());
    }

    // TODO path must be ordered(and legal to contain duplicates like "a.b.a"; list would be better
    public void remove(Collection<String> path) {
        checkPath(path);

        if (log.isTraceEnabled()) {
            log.trace("removing sensor {} for {}", new Object[] {path, entity});
        }

        values.remove(path);
    }

    /**
     * Gets the value
     *
     * @param path the path of the value to get
     * @return the value
     * @throws IllegalArgumentException path is null or empty.
     */
    public Object getValue(Collection<String> path) {
        // TODO previously this would return a map of the sub-tree if the path matched a prefix of a group of sensors, 
        // or the leaf value if only one value. Arguably that is not required - what is/was the use-case?
        // 
        checkPath(path);
        Object result = values.get(path);
        return (isNull(result)) ? null : result;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(AttributeSensor<T> sensor) {
        return (T) TypeCoercions.coerce(getValue(sensor.getNameParts()), sensor.getType());
    }

    @SuppressWarnings("unchecked")
    private <T> T typedNull() {
        return (T) Marker.NULL;
    }
    
    private boolean isNull(Object t) {
        return t == Marker.NULL;
    }
}
