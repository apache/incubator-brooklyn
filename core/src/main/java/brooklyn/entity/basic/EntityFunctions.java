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
import brooklyn.event.AttributeSensor;
import brooklyn.management.ManagementContext;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.base.Supplier;

public class EntityFunctions {

    public static <T> Function<Entity, T> attribute(final AttributeSensor<T> attribute) {
        return new Function<Entity, T>() {
            @Override public T apply(Entity input) {
                return (input == null) ? null : input.getAttribute(attribute);
            }
        };
    }
    
    public static <T> Function<Entity, T> config(final ConfigKey<T> key) {
        return new Function<Entity, T>() {
            @Override public T apply(Entity input) {
                return (input == null) ? null : input.getConfig(key);
            }
        };
    }
    
    public static Function<Entity, String> displayName() {
        return new Function<Entity, String>() {
            @Override public String apply(Entity input) {
                return (input == null) ? null : input.getDisplayName();
            }
        };
    }
    
    public static Function<Entity, String> id() {
        return new Function<Entity, String>() {
            @Override public String apply(Entity input) {
                return (input == null) ? null : input.getId();
            }
        };
    }

    /** returns a function which sets the given sensors on the entity passed in,
     * with {@link Entities#UNCHANGED} and {@link Entities#REMOVE} doing those actions. */
    public static Function<Entity,Void> settingSensorsConstant(final Map<AttributeSensor<?>,Object> values) {
        checkNotNull(values, "values");
        return new Function<Entity,Void>() {
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
        };
    }

    /** as {@link #settingSensorsConstant(Map)} but as a {@link Runnable} */
    public static Runnable settingSensorsConstantRunnable(final Entity entity, final Map<AttributeSensor<?>,Object> values) {
        checkNotNull(entity, "entity");
        checkNotNull(values, "values");
        return new Runnable() {
            @Override
            public void run() {
                settingSensorsConstant(values).apply(entity);
            }
        };
    }


    /** as {@link #settingSensorsConstant(Map)} but creating a {@link Function} which ignores its input,
     * suitable for use with sensor feeds where the input is ignored */
    public static <T> Function<T,Void> settingSensorsConstantFunction(final Entity entity, final Map<AttributeSensor<?>,Object> values) {
        checkNotNull(entity, "entity");
        checkNotNull(values, "values");
        return new Function<T,Void>() {
            @Override
            public Void apply(T input) {
                return settingSensorsConstant(values).apply(entity);
            }
        };
    }

    public static Supplier<Collection<Application>> applications(final ManagementContext mgmt) {
        return new Supplier<Collection<Application>>() {
            @Override
            public Collection<Application> get() {
                return mgmt.getApplications();
            }
        };
    }

}
