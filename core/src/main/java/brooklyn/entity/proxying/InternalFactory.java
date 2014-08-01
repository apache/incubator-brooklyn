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

import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.Optional;

/**
 */
public class InternalFactory {

    protected final ManagementContextInternal managementContext;

    /**
     * For tracking if constructor has been called by framework, or in legacy way (i.e. directly).
     * 
     * To be deleted once we delete support for constructing directly (and expecting configure() to be
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
     * Returns true if this is a "new-style" policy (i.e. where not expected callers to use the constructor directly to instantiate it).
     * 
     * @param clazz
     */
    public static boolean isNewStyle(Class<?> clazz) {
        try {
            clazz.getConstructor(new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    public InternalFactory(ManagementContextInternal managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    /**
     * Constructs an instance (e.g. of entity, location, enricher or policy.
     * If new-style, calls no-arg constructor; if old-style, uses spec to pass in config.
     */
    protected <T> T construct(Class<? extends T> clazz, Map<String, ?> constructorFlags) {
        try {
            if (isNewStyle(clazz)) {
                return constructNewStyle(clazz);
            } else {
                return constructOldStyle(clazz, MutableMap.copyOf(constructorFlags));
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
         }
     }

    /**
     * Constructs a new instance (fails if no no-arg constructor).
     */
    protected <T> T constructNewStyle(Class<T> clazz) {
        if (!isNewStyle(clazz)) {
            throw new IllegalStateException("Class "+clazz+" must have a no-arg constructor");
        }
        
        try {
            FactoryConstructionTracker.setConstructing();
            try {
                return clazz.newInstance();
            } finally {
                FactoryConstructionTracker.reset();
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    protected <T> T constructOldStyle(Class<T> clazz, Map<String,?> flags) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        FactoryConstructionTracker.setConstructing();
        Optional<T> v;
        try {
            v = Reflections.invokeConstructorWithArgs(clazz, new Object[] {MutableMap.copyOf(flags)}, true);
        } finally {
            FactoryConstructionTracker.reset();
        }
        if (v.isPresent()) {
            return v.get();
        } else {
            throw new IllegalStateException("No valid constructor defined for "+clazz+" (expected no-arg or single java.util.Map argument)");
        }
    }
}
