/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.core.config;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.EntityAdjunct;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.objs.AbstractEntityAdjunct;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal;
import org.apache.brooklyn.core.objs.BrooklynObjectPredicate;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Checks configuration constraints on entities and their adjuncts.
 *
 * @since 0.9.0
 */
public abstract class ConfigConstraints<T extends BrooklynObject> {

    public static final Logger LOG = LoggerFactory.getLogger(ConfigConstraints.class);

    private final T brooklynObject;

    /**
     * Checks all constraints of all config keys available to an entity.
     * <p>
     * If a constraint is a {@link BrooklynObjectPredicate} then
     * {@link BrooklynObjectPredicate#apply(Object, BrooklynObject)} will be used.
     */
    public static void assertValid(Entity entity) {
        Iterable<ConfigKey<?>> violations = new EntityConfigConstraints(entity).getViolations();
        if (!Iterables.isEmpty(violations)) {
            throw new ConstraintViolationException(errorMessage(entity, violations));
        }
    }

    /**
     * Checks all constraints of all config keys available to an entity adjunct.
     * <p>
     * If a constraint is a {@link BrooklynObjectPredicate} then
     * {@link BrooklynObjectPredicate#apply(Object, BrooklynObject)} will be used.
     */
    public static void assertValid(EntityAdjunct adjunct) {
        Iterable<ConfigKey<?>> violations = new EntityAdjunctConstraints(adjunct).getViolations();
        if (!Iterables.isEmpty(violations)) {
            throw new ConstraintViolationException(errorMessage(adjunct, violations));
        }
    }

    public static <T> void assertValid(Entity entity, ConfigKey<T> key, T value) {
        if (!new EntityConfigConstraints(entity).isValueValid(key, value)) {
            throw new ConstraintViolationException("Invalid value for " + key + " on " + entity + ": " + value);
        }
    }

    public static <T> void assertValid(Location location, ConfigKey<T> key, T value) {
        if (!new LocationConfigConstraints(location).isValueValid(key, value)) {
            throw new ConstraintViolationException("Invalid value for " + key + " on " + location + ": " + value);
        }
    }

    private static String errorMessage(BrooklynObject object, Iterable<ConfigKey<?>> violations) {
        StringBuilder message = new StringBuilder("Error configuring ")
                .append(object.getDisplayName())
                .append(": [");
        Iterator<ConfigKey<?>> it = violations.iterator();
        while (it.hasNext()) {
            ConfigKey<?> config = it.next();
            message.append(config.getName())
                    .append(":")
                    .append(config.getConstraint());
            if (it.hasNext()) {
                message.append(", ");
            }
        }
        return message.append("]").toString();
    }

    public ConfigConstraints(T brooklynObject) {
        this.brooklynObject = brooklynObject;
    }

    abstract Iterable<ConfigKey<?>> getBrooklynObjectTypeConfigKeys();

    public Iterable<ConfigKey<?>> getViolations() {
        return validateAll();
    }

    @SuppressWarnings("unchecked")
    private Iterable<ConfigKey<?>> validateAll() {
        List<ConfigKey<?>> violating = Lists.newLinkedList();
        Iterable<ConfigKey<?>> configKeys = getBrooklynObjectTypeConfigKeys();
        LOG.trace("Checking config keys on {}: {}", getBrooklynObject(), configKeys);
        for (ConfigKey<?> configKey : configKeys) {
            BrooklynObjectInternal.ConfigurationSupportInternal configInternal = getConfigurationSupportInternal();
            // getNonBlocking method coerces the value to the config key's type.
            Maybe<?> maybeValue = configInternal.getNonBlocking(configKey);
            if (maybeValue.isPresent()) {
                // Cast is safe because the author of the constraint on the config key had to
                // keep its type to Predicte<? super T>, where T is ConfigKey<T>.
                ConfigKey<Object> ck = (ConfigKey<Object>) configKey;
                if (!isValueValid(ck, maybeValue.get())) {
                    violating.add(configKey);
                }
            }
        }
        return violating;
    }

    @SuppressWarnings("unchecked")
    <V> boolean isValueValid(ConfigKey<V> configKey, V value) {
        try {
            Predicate<? super V> po = configKey.getConstraint();
            if (po instanceof BrooklynObjectPredicate) {
                return BrooklynObjectPredicate.class.cast(po).apply(value, brooklynObject);
            } else {
                return po.apply(value);
            }
        } catch (Exception e) {
            LOG.debug("Error checking constraint on " + configKey.getName(), e);
        }
        return true;
    }

    private BrooklynObjectInternal.ConfigurationSupportInternal getConfigurationSupportInternal() {
        return ((BrooklynObjectInternal) brooklynObject).config();
    }

    protected T getBrooklynObject() {
        return brooklynObject;
    }

    private static class EntityConfigConstraints extends ConfigConstraints<Entity> {
        public EntityConfigConstraints(Entity brooklynObject) {
            super(brooklynObject);
        }

        @Override
        Iterable<ConfigKey<?>> getBrooklynObjectTypeConfigKeys() {
            return getBrooklynObject().getEntityType().getConfigKeys();
        }
    }

    private static class EntityAdjunctConstraints extends ConfigConstraints<EntityAdjunct> {
        public EntityAdjunctConstraints(EntityAdjunct brooklynObject) {
            super(brooklynObject);
        }

        @Override
        Iterable<ConfigKey<?>> getBrooklynObjectTypeConfigKeys() {
            return ((AbstractEntityAdjunct) getBrooklynObject()).getAdjunctType().getConfigKeys();
        }
    }

    private static class LocationConfigConstraints extends ConfigConstraints<Location> {
        public LocationConfigConstraints(Location brooklynObject) {
            super(brooklynObject);
        }

        @Override
        Iterable<ConfigKey<?>> getBrooklynObjectTypeConfigKeys() {
            return Collections.emptyList();
        }
    }

}
