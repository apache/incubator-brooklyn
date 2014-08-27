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
package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator.InstantiatorFromKey;

import java.util.List;
import java.util.Map;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableList;
import brooklyn.util.config.ConfigBag;

import com.google.common.annotations.Beta;

/**
 * Pattern for resolving "decorations" on service specs / entity specs, such as policies, enrichers, etc.
 * @since 0.7.0
 */
@Beta
public abstract class BrooklynEntityDecorationResolver<DT> {

    public final BrooklynYamlTypeInstantiator.Factory instantiator;
    
    protected BrooklynEntityDecorationResolver(BrooklynYamlTypeInstantiator.Factory instantiator) {
        this.instantiator = instantiator;
    }
    
    public abstract void decorate(EntitySpec<?> entitySpec, ConfigBag attrs);

    protected Iterable<? extends DT> buildListOfTheseDecorationsFromEntityAttributes(ConfigBag attrs) {
        Object value = getDecorationAttributeJsonValue(attrs); 
        List<DT> decorations = MutableList.of();
        if (value==null) return decorations;
        if (value instanceof Iterable) {
            for (Object decorationJson: (Iterable<?>)value)
                addDecorationFromJsonMap(checkIsMap(decorationJson), decorations);
        } else {
            // in future may support types other than iterables here, 
            // e.g. a map short form where the key is the type
            throw new IllegalArgumentException(getDecorationKind()+" body should be iterable, not " + value.getClass());
        }
        return decorations;
    }
    
    protected Map<?,?> checkIsMap(Object decorationJson) {
        if (!(decorationJson instanceof Map))
            throw new IllegalArgumentException(getDecorationKind()+" value must be a Map, not " + 
                (decorationJson==null ? null : decorationJson.getClass()) );
        return (Map<?,?>) decorationJson;
    }

    protected abstract String getDecorationKind();
    protected abstract Object getDecorationAttributeJsonValue(ConfigBag attrs);
    
    /** creates and adds decorations from the given json to the given collection; 
     * default impl requires a map and calls {@link #addDecorationFromJsonMap(Map, List)} */
    protected void addDecorationFromJson(Object decorationJson, List<DT> decorations) {
        addDecorationFromJsonMap(checkIsMap(decorationJson), decorations);
    }
    protected abstract void addDecorationFromJsonMap(Map<?,?> decorationJson, List<DT> decorations);
    

    public static class PolicySpecResolver extends BrooklynEntityDecorationResolver<PolicySpec<?>> {
        
        protected PolicySpecResolver(BrooklynYamlTypeInstantiator.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Policy"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.policySpecs(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey("brooklyn.policies");
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<PolicySpec<?>> decorations) {
            InstantiatorFromKey decoLoader = instantiator.from(decorationJson).prefix("policy");

            String policyType = decoLoader.getTypeName().get();
            ManagementContext mgmt = instantiator.loader.getManagementContext();
            BrooklynCatalog catalog = mgmt.getCatalog();
            CatalogItem<?, ?> item = catalog.getCatalogItem(policyType);
            PolicySpec<? extends Policy> spec;
            if (item != null) {
                spec = (PolicySpec<? extends Policy>) catalog.createSpec(item);
                spec.configure(decoLoader.getConfigMap());
            } else {
                // this pattern of creating a spec could be simplified with a "Configurable" superinterface on *Spec  
                spec = PolicySpec.create(decoLoader.getType(Policy.class))
                    .configure( decoLoader.getConfigMap() );
            }
            decorations.add(spec);
        }
    }

    public static class EnricherSpecResolver extends BrooklynEntityDecorationResolver<EnricherSpec<?>> {
        
        protected EnricherSpecResolver(BrooklynYamlTypeInstantiator.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Enricher"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.enricherSpecs(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey("brooklyn.enrichers");
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<EnricherSpec<?>> decorations) {
            InstantiatorFromKey decoLoader = instantiator.from(decorationJson).prefix("enricher");
            decorations.add(EnricherSpec.create(decoLoader.getType(Enricher.class))
                .configure( decoLoader.getConfigMap() ));
        }
    }
    
    public static class InitializerResolver extends BrooklynEntityDecorationResolver<EntityInitializer> {
        
        protected InitializerResolver(BrooklynYamlTypeInstantiator.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Entity initializer"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.addInitializers(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey("brooklyn.initializers");
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<EntityInitializer> decorations) {
            decorations.add(instantiator.from(decorationJson).prefix("initializer").newInstance(EntityInitializer.class));
        }
    }
    

}
