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
public class InternalPolicyFactory extends InternalFactory {

    /**
     * Returns true if this is a "new-style" policy (i.e. where not expected to call the constructor to instantiate it).
     * 
     * @param managementContext
     * @param clazz
     * 
     * @deprecated since 0.7.0; use {@link InternalFactory#isNewStyle(Class)}
     */
    @Deprecated
    public static boolean isNewStylePolicy(ManagementContext managementContext, Class<?> clazz) {
        try {
            return isNewStylePolicy(clazz);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * @deprecated since 0.7.0; use {@link InternalFactory#isNewStyle(Class)}
     */
    @Deprecated
    public static boolean isNewStylePolicy(Class<?> clazz) {
        if (!Policy.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not a policy");
        }

        return InternalFactory.isNewStyle(clazz);
    }
    
    /**
     * @deprecated since 0.7.0; use {@link InternalFactory#isNewStyle(Class)}
     */
    @Deprecated
    public static boolean isNewStyleEnricher(Class<?> clazz) {
        if (!Enricher.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not an enricher");
        }
        
        return InternalFactory.isNewStyle(clazz);
    }
    
    public InternalPolicyFactory(ManagementContextInternal managementContext) {
        super(managementContext);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends Policy> T createPolicy(PolicySpec<T> spec) {
        if (spec.getFlags().containsKey("parent")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent; use spec.parent() instead for "+spec);
        }
        
        try {
            Class<? extends T> clazz = spec.getType();
            
            T pol = construct(clazz, spec.getFlags());

            if (spec.getDisplayName()!=null)
                ((AbstractPolicy)pol).setDisplayName(spec.getDisplayName());
            
            pol.getTagSupport().addTags(spec.getTags());
            
            if (isNewStyle(clazz)) {
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
            
            T enricher = construct(clazz, spec.getFlags());
            
            if (spec.getDisplayName()!=null)
                ((AbstractEnricher)enricher).setDisplayName(spec.getDisplayName());
            
            enricher.getTagSupport().addTags(spec.getTags());
            
            if (isNewStyle(clazz)) {
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
        return super.constructNewStyle(clazz);
    }
    
    /**
     * Constructs a new-style enricher (fails if no no-arg constructor).
     */
    public <T extends Enricher> T constructEnricher(Class<T> clazz) {
        return super.constructNewStyle(clazz);
    }
}
