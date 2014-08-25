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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.Identifiable;
import brooklyn.event.AttributeSensor;
import brooklyn.management.ManagementContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Functionals;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class EntityFunctions {

    public static <T> Function<Entity, T> attribute(final AttributeSensor<T> attribute) {
        class GetEntityAttributeFunction implements Function<Entity, T> {
            @Override public T apply(Entity input) {
                return (input == null) ? null : input.getAttribute(attribute);
            }
        };
        return new GetEntityAttributeFunction();
    }
    
    public static <T> Function<Entity, T> config(final ConfigKey<T> key) {
        class GetEntityConfigFunction implements Function<Entity, T> {
            @Override public T apply(Entity input) {
                return (input == null) ? null : input.getConfig(key);
            }
        };
        return new GetEntityConfigFunction();
    }
    
    public static Function<Entity, String> displayName() {
        class GetEntityDisplayName implements Function<Entity, String> {
            @Override public String apply(Entity input) {
                return (input == null) ? null : input.getDisplayName();
            }
        };
        return new GetEntityDisplayName();
    }
    
    public static Function<Identifiable, String> id() {
        class GetIdFunction implements Function<Identifiable, String> {
            @Override public String apply(Identifiable input) {
                return (input == null) ? null : input.getId();
            }
        };
        return new GetIdFunction();
    }

    /** returns a function which sets the given sensors on the entity passed in,
     * with {@link Entities#UNCHANGED} and {@link Entities#REMOVE} doing those actions. */
    public static Function<Entity,Void> settingSensorsConstant(final Map<AttributeSensor<?>,Object> values) {
        checkNotNull(values, "values");
        class SettingSensorsConstantFunction implements Function<Entity, Void> {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override public Void apply(Entity input) {
                for (Map.Entry<AttributeSensor<?>,Object> entry : values.entrySet()) {
                    AttributeSensor sensor = (AttributeSensor)entry.getKey();
                    Object value = entry.getValue();
                    if (value==Entities.UNCHANGED) {
                        // nothing
                    } else if (value==Entities.REMOVE) {
                        ((EntityInternal)input).removeAttribute(sensor);
                    } else {
                        value = TypeCoercions.coerce(value, sensor.getType());
                        ((EntityInternal)input).setAttribute(sensor, value);
                    }
                }
                return null;
            }
        }
        return new SettingSensorsConstantFunction();
    }

    /** as {@link #settingSensorsConstant(Map)} but as a {@link Runnable} */
    public static Runnable settingSensorsConstant(final Entity entity, final Map<AttributeSensor<?>,Object> values) {
        checkNotNull(entity, "entity");
        checkNotNull(values, "values");
        return Functionals.runnable(Suppliers.compose(settingSensorsConstant(values), Suppliers.ofInstance(entity)));
    }

    public static <K,V> Function<Entity, Void> updatingSensorMapEntry(final AttributeSensor<Map<K,V>> mapSensor, final K key, final Supplier<? extends V> valueSupplier) {
        class UpdatingSensorMapEntryFunction implements Function<Entity, Void> {
            @Override public Void apply(Entity input) {
                ServiceStateLogic.updateMapSensorEntry((EntityLocal)input, mapSensor, key, valueSupplier.get());
                return null;
            }
        }
        return new UpdatingSensorMapEntryFunction();
    }
    public static <K,V> Runnable updatingSensorMapEntry(final Entity entity, final AttributeSensor<Map<K,V>> mapSensor, final K key, final Supplier<? extends V> valueSupplier) {
        return Functionals.runnable(Suppliers.compose(updatingSensorMapEntry(mapSensor, key, valueSupplier), Suppliers.ofInstance(entity)));
    }

    public static Supplier<Collection<Application>> applications(final ManagementContext mgmt) {
        class AppsSupplier implements Supplier<Collection<Application>> {
            @Override
            public Collection<Application> get() {
                return mgmt.getApplications();
            }
        }
        return new AppsSupplier();
    }
}
