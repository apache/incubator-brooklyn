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
package brooklyn.event.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A {@link Map} of {@link Entity} attribute values.
 */
public final class AttributeMap implements Serializable {

    private static final long serialVersionUID = -6834883734250888344L;

    static final Logger log = LoggerFactory.getLogger(AttributeMap.class);

    private static enum Marker {
        NULL;
    }
    
    private final AbstractEntity entity;

    // Note that we synchronize on the top-level map, to handle concurrent updates and and gets (ENGR-2111)
    private final Map<Collection<String>, Object> values;

    /**
     * Creates a new AttributeMap.
     *
     * @param entity the EntityLocal this AttributeMap belongs to.
     * @throws IllegalArgumentException if entity is null
     */
    public AttributeMap(AbstractEntity entity, Map<Collection<String>, Object> storage) {
        this.entity = checkNotNull(entity, "entity must be specified");
        this.values = checkNotNull(storage, "storage map must not be null");
    }

    public Map<Collection<String>, Object> asRawMap() {
        return ImmutableMap.copyOf(values);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<Collection<String>, Object> entry : values.entrySet()) {
            String sensorName = Joiner.on('.').join(entry.getKey());
            Object val = (isNull(entry.getValue())) ? null : entry.getValue();
            result.put(sensorName, val);
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

    public void remove(AttributeSensor<?> attribute) {
        if (log.isDebugEnabled()) {
            log.debug("removing attribute {} on {}", attribute.getName(), entity);
        }

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
