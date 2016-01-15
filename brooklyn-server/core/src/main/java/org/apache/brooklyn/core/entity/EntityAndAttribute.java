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
package org.apache.brooklyn.core.entity;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;

/**
 * A tuple containing an {@link Entity} and an {@link AttributeSensor}, which is assumed to be present on the entity.
 * <p>
 * Allows retrieval of the attribute {@link #getValue() value} or can be used instead where a {@link Supplier} for
 * the attribute value is required.
 */
public class EntityAndAttribute<T> implements Supplier<T> {

    private final Entity entity;
    private final AttributeSensor<T> attribute;

    public static <T> EntityAndAttribute<T> create(Entity entity, AttributeSensor<T> attribute) {
        return new EntityAndAttribute<T>(entity, attribute);
    }

    /**
     * @deprecated since 0.7.0; use {@link #create(Entity, AttributeSensor)}; this does not relate to {@link Supplier}
     */
    public static <T> EntityAndAttribute<T> supplier(Entity entity, AttributeSensor<T> attribute) {
        return create(entity, attribute);
    }

    public EntityAndAttribute(Entity entity, AttributeSensor<T> attribute) {
        this.entity = checkNotNull(entity, "entity");
        this.attribute = checkNotNull(attribute, "attribute");
    }

    public Entity getEntity() {
        return entity;
    }

    public AttributeSensor<T> getAttribute() {
        return attribute;
    }

    public T getValue() {
        return entity.getAttribute(attribute);
    }

    public void setValue(T val) {
        ((EntityLocal)entity).sensors().set(attribute, val);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the current value of the {@link #getAttribute() attribute} on the {@link #getEntity() entity}.
     *
     * @see #getValue()
     */
    @Override
    public T get() {
        return getValue();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("entity", entity)
                .add("attribute", attribute)
                .toString();
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(entity, attribute);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof EntityAndAttribute)) return false;
        EntityAndAttribute<?> that = (EntityAndAttribute<?>) o;
        return Objects.equal(this.entity, that.entity) &&
                Objects.equal(this.attribute, that.attribute);
    }

}
