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

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ConfigConstraints {

    public static final Logger LOG = LoggerFactory.getLogger(ConfigConstraints.class);
    private final Entity entity;

    public ConfigConstraints(Entity e) {
        this.entity = e;
    }

    /**
     * Checks all constraints of all config keys available to an entity.
     */
    public static void assertValid(Entity e) {
        Iterable<ConfigKey<?>> violations = new ConfigConstraints(e).getViolations();
        if (!Iterables.isEmpty(violations)) {
            throw new AssertionError("ConfigKeys violate constraints: " + violations);
        }
    }

    public boolean isValid() {
        return Iterables.isEmpty(getViolations());
    }

    public Iterable<ConfigKey<?>> getViolations() {
        return validateAll();
    }

    @SuppressWarnings("unchecked")
    private Iterable<ConfigKey<?>> validateAll() {
        EntityInternal ei = (EntityInternal) entity;
        List<ConfigKey<?>> violating = Lists.newArrayList();

        for (ConfigKey<?> configKey : getEntityConfigKeys(entity)) {
            // getRaw returns null if explicitly set and absent if config key was unset.
            Object value = ei.config().getRaw(configKey).or(configKey.getDefaultValue());

            if (value == null || value.getClass().isAssignableFrom(configKey.getType())) {
                // Cast should be safe because the author of the constraint on the config key had to
                // keep its type to Predicte<? super T>, where T is ConfigKey<T>.
                try {
                    Predicate<Object> po = (Predicate<Object>) configKey.getConstraint();
                    if (!po.apply(value)) {
                        violating.add(configKey);
                    }
                } catch (Exception e) {
                    LOG.debug("Error checking constraint on {} {} ", configKey.getName(), e);
                }
            }
        }
        return violating;
    }

    private static Iterable<ConfigKey<?>> getEntityConfigKeys(Entity entity) {
        return entity.getEntityType().getConfigKeys();
    }

}
