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
package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Map.Entry;

import brooklyn.config.ConfigKey;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalLocationManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

/**
 * Creates locations of required types.
 * 
 * This is an internal class for use by core-brooklyn. End-users are strongly discouraged from
 * using this class directly.
 * 
 * @author aled
 */
public class InternalLocationFactory {

    private final ManagementContextInternal managementContext;

    /**
     * For tracking if AbstractLocation constructor has been called by framework, or in legacy way (i.e. directly).
     * 
     * To be deleted once we delete support for constructing locations directly (and expecting configure() to be
     * called inside the constructor, etc).
     * 
     * @author aled
     */
    public static class FactoryConstructionTracker {
        private static ThreadLocal<Boolean> constructing = new ThreadLocal<Boolean>();
        
        public static boolean isConstructing() {
            return (constructing.get() == Boolean.TRUE);
        }
        
        static void reset() {
            constructing.set(Boolean.FALSE);
        }
        
        static void setConstructing() {
            constructing.set(Boolean.TRUE);
        }
    }

    /**
     * Returns true if this is a "new-style" location (i.e. where not expected to call the constructor to instantiate it).
     * 
     * @param managementContext
     * @param clazz
     */
    public static boolean isNewStyleLocation(ManagementContext managementContext, Class<?> clazz) {
        try {
            return isNewStyleLocation(clazz);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    public static boolean isNewStyleLocation(Class<?> clazz) {
        if (!Location.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not an location");
        }
        
        try {
            clazz.getConstructor(new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    public InternalLocationFactory(ManagementContextInternal managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends Location> T createLocation(LocationSpec<T> spec) {
        if (spec.getFlags().containsKey("parent")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent; use spec.parent() instead for "+spec);
        }
        if (spec.getFlags().containsKey("id")) {
            throw new IllegalArgumentException("Spec's flags must not contain id; use spec.id() instead for "+spec);
        }
        if (spec.getId() != null && ((LocalLocationManager)managementContext.getLocationManager()).isKnownLocationId(spec.getId())) {
            throw new IllegalArgumentException("Entity with id "+spec.getId()+" already exists; cannot create new entity with this explicit id from spec "+spec);
        }

        try {
            Class<? extends T> clazz = spec.getType();
            
            T loc;
            if (isNewStyleLocation(clazz)) {
                loc = constructLocation(clazz);
            } else {
                loc = constructOldStyle(clazz, MutableMap.copyOf(spec.getFlags()));
            }

            if (spec.getId() != null) {
                FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", spec.getId()), loc);
            }
            managementContext.prePreManage(loc);

            if (spec.getDisplayName()!=null)
                ((AbstractLocation)loc).setName(spec.getDisplayName());
            
            if (isNewStyleLocation(clazz)) {
                ((AbstractLocation)loc).setManagementContext(managementContext);
                ((AbstractLocation)loc).configure(ConfigBag.newInstance().putAll(spec.getFlags()).putAll(spec.getConfig()).getAllConfig());
            }
            
            for (Map.Entry<ConfigKey<?>, Object> entry : spec.getConfig().entrySet()) {
                ((AbstractLocation)loc).setConfig((ConfigKey)entry.getKey(), entry.getValue());
            }
            for (Entry<Class<?>, Object> entry : spec.getExtensions().entrySet()) {
                ((LocationInternal)loc).addExtension((Class)entry.getKey(), entry.getValue());
            }
            ((AbstractLocation)loc).init();
            
            Location parent = spec.getParent();
            if (parent != null) {
                loc.setParent(parent);
            }
            return loc;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    /**
     * Constructs a new-style location (fails if no no-arg constructor).
     */
    public <T extends Location> T constructLocation(Class<T> clazz) {
        try {
            FactoryConstructionTracker.setConstructing();
            try {
                if (isNewStyleLocation(clazz)) {
                    return clazz.newInstance();
                } else {
                    throw new IllegalStateException("Location class "+clazz+" must have a no-arg constructor");
                }
            } finally {
                FactoryConstructionTracker.reset();
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    private <T extends Location> T constructOldStyle(Class<? extends T> clazz, Map<String,?> flags) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (flags.containsKey("parentLocation")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+clazz);
        }
        Optional<? extends T> v = Reflections.invokeConstructorWithArgs(clazz, new Object[] {MutableMap.copyOf(flags)}, true);
        if (v.isPresent()) {
            return v.get();
        } else {
            throw new IllegalStateException("No valid constructor defined for "+clazz+" (expected no-arg or single java.util.Map argument)");
        }
    }
}
