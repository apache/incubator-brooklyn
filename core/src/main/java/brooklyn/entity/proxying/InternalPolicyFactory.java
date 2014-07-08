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

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.Optional;

/**
 * Creates policies of required types.
 * 
 * This is an internal class for use by core-brooklyn. End-users are strongly discouraged from
 * using this class directly.
 * 
 * Note that calling policies by their constructors has not been "deprecated" (yet!). We just
 * support both mechanisms, so one can supply PolicySpec in an EntitySpec.
 * 
 * @author aled
 */
public class InternalPolicyFactory {

    private final ManagementContextInternal managementContext;

    /**
     * For tracking if AbstractPolicy constructor has been called by framework, or in legacy way (i.e. directly).
     * 
     * To be deleted once we delete support for constructing policies directly (and expecting configure() to be
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
     * Returns true if this is a "new-style" policy (i.e. where not expected to call the constructor to instantiate it).
     * 
     * @param managementContext
     * @param clazz
     */
    public static boolean isNewStylePolicy(ManagementContext managementContext, Class<?> clazz) {
        try {
            return isNewStylePolicy(clazz);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    public static boolean isNewStylePolicy(Class<?> clazz) {
        if (!Policy.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not a policy");
        }
        
        try {
            clazz.getConstructor(new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    public static boolean isNewStyleEnricher(Class<?> clazz) {
        if (!Enricher.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not an enricher");
        }
        
        try {
            clazz.getConstructor(new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    public InternalPolicyFactory(ManagementContextInternal managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends Policy> T createPolicy(PolicySpec<T> spec) {
        if (spec.getFlags().containsKey("parent")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent; use spec.parent() instead for "+spec);
        }
        
        try {
            Class<? extends T> clazz = spec.getType();
            
            T pol;
            if (isNewStylePolicy(clazz)) {
                pol = constructPolicy(clazz);
            } else {
                pol = constructOldStyle(clazz, MutableMap.copyOf(spec.getFlags()));
            }

            if (spec.getDisplayName()!=null)
                ((AbstractPolicy)pol).setName(spec.getDisplayName());
            
            if (isNewStylePolicy(clazz)) {
                ((AbstractPolicy)pol).setManagementContext(managementContext);
                Map<String, Object> config = ConfigBag.newInstance().putAll(spec.getFlags()).putAll(spec.getConfig()).getAllConfig();
                ((AbstractPolicy)pol).configure(MutableMap.copyOf(config)); // TODO AbstractPolicy.configure modifies the map
            }
            
            // TODO Can we avoid this for "new-style policies"? Should we just trust the configure() method, 
            // which the user may have overridden? 
            // Also see InternalLocationFactory for same issue, which this code is based on.
            for (Map.Entry<ConfigKey<?>, Object> entry : spec.getConfig().entrySet()) {
                ((AbstractPolicy)pol).setConfig((ConfigKey)entry.getKey(), entry.getValue());
            }
            ((AbstractPolicy)pol).init();
            
            return pol;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends Enricher> T createEnricher(EnricherSpec<T> spec) {
        if (spec.getFlags().containsKey("parent")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent; use spec.parent() instead for "+spec);
        }
        
        try {
            Class<? extends T> clazz = spec.getType();
            
            T enricher;
            if (isNewStyleEnricher(clazz)) {
                enricher = constructEnricher(clazz);
            } else {
                enricher = constructOldStyle(clazz, MutableMap.copyOf(spec.getFlags()));
            }
            
            if (spec.getDisplayName()!=null)
                ((AbstractEnricher)enricher).setName(spec.getDisplayName());
            
            if (isNewStyleEnricher(clazz)) {
                ((AbstractEnricher)enricher).setManagementContext(managementContext);
                Map<String, Object> config = ConfigBag.newInstance().putAll(spec.getFlags()).putAll(spec.getConfig()).getAllConfig();
                ((AbstractEnricher)enricher).configure(MutableMap.copyOf(config)); // TODO AbstractEnricher.configure modifies the map
            }
            
            // TODO Can we avoid this for "new-style policies"? Should we just trust the configure() method, 
            // which the user may have overridden? 
            // Also see InternalLocationFactory for same issue, which this code is based on.
            for (Map.Entry<ConfigKey<?>, Object> entry : spec.getConfig().entrySet()) {
                ((AbstractEnricher)enricher).setConfig((ConfigKey)entry.getKey(), entry.getValue());
            }
            ((AbstractEnricher)enricher).init();
            
            return enricher;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    /**
     * Constructs a new-style policy (fails if no no-arg constructor).
     */
    public <T extends Policy> T constructPolicy(Class<T> clazz) {
        try {
            FactoryConstructionTracker.setConstructing();
            try {
                if (isNewStylePolicy(clazz)) {
                    return clazz.newInstance();
                } else {
                    throw new IllegalStateException("Policy class "+clazz+" must have a no-arg constructor");
                }
            } finally {
                FactoryConstructionTracker.reset();
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    /**
     * Constructs a new-style enricher (fails if no no-arg constructor).
     */
    public <T extends Enricher> T constructEnricher(Class<T> clazz) {
        try {
            FactoryConstructionTracker.setConstructing();
            try {
                if (isNewStyleEnricher(clazz)) {
                    return clazz.newInstance();
                } else {
                    throw new IllegalStateException("Enricher class "+clazz+" must have a no-arg constructor");
                }
            } finally {
                FactoryConstructionTracker.reset();
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    private <T> T constructOldStyle(Class<T> clazz, Map<String,?> flags) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Optional<T> v = Reflections.invokeConstructorWithArgs(clazz, new Object[] {MutableMap.copyOf(flags)}, true);
        if (v.isPresent()) {
            return v.get();
        } else {
            throw new IllegalStateException("No valid constructor defined for "+clazz+" (expected no-arg or single java.util.Map argument)");
        }
    }
}
