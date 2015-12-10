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
package org.apache.brooklyn.camp.brooklyn.spi.creation;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntityInitializer;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator.InstantiatorFromKey;
import org.apache.brooklyn.core.objs.BasicSpecParameter;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

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

    protected List<? extends DT> buildListOfTheseDecorationsFromEntityAttributes(ConfigBag attrs) {
        Object value = getDecorationAttributeJsonValue(attrs); 
        if (value==null) return MutableList.of();
        if (value instanceof Iterable) {
            return buildListOfTheseDecorationsFromIterable((Iterable<?>)value);
        } else {
            // in future may support types other than iterables here, 
            // e.g. a map short form where the key is the type
            throw new IllegalArgumentException(getDecorationKind()+" body should be iterable, not " + value.getClass());
        }
    }

    protected Map<?,?> checkIsMap(Object decorationJson) {
        if (!(decorationJson instanceof Map))
            throw new IllegalArgumentException(getDecorationKind()+" value must be a Map, not " + 
                (decorationJson==null ? null : decorationJson.getClass()) );
        return (Map<?,?>) decorationJson;
    }

    protected List<DT> buildListOfTheseDecorationsFromIterable(Iterable<?> value) {
        List<DT> decorations = MutableList.of();
        for (Object decorationJson: value)
            addDecorationFromJsonMap(checkIsMap(decorationJson), decorations);
        return decorations;
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
        
        public PolicySpecResolver(BrooklynYamlTypeInstantiator.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Policy"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.policySpecs(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_POLICIES);
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<PolicySpec<?>> decorations) {
            InstantiatorFromKey decoLoader = instantiator.from(decorationJson).prefix("policy");

            String policyType = decoLoader.getTypeName().get();
            ManagementContext mgmt = instantiator.loader.getManagementContext();
            
            Maybe<RegisteredType> item = RegisteredTypes.tryValidate(mgmt.getTypeRegistry().get(policyType), RegisteredTypeLoadingContexts.spec(Policy.class));
            PolicySpec<?> spec;
            if (item.get()!=null) {
                spec = mgmt.getTypeRegistry().createSpec(item.get(), null, PolicySpec.class);
            } else {
                Class<? extends Policy> type = decoLoader.getType(Policy.class);
                spec = PolicySpec.create(type)
                        .parameters(BasicSpecParameter.fromClass(mgmt, type));
            }
            spec.configure( decoLoader.getConfigMap() );
            decorations.add(spec);
        }
    }

    public static class EnricherSpecResolver extends BrooklynEntityDecorationResolver<EnricherSpec<?>> {
        
        public EnricherSpecResolver(BrooklynYamlTypeInstantiator.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Enricher"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.enricherSpecs(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_ENRICHERS);
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<EnricherSpec<?>> decorations) {
            InstantiatorFromKey decoLoader = instantiator.from(decorationJson).prefix("enricher");
            Class<? extends Enricher> type = decoLoader.getType(Enricher.class);
            decorations.add(EnricherSpec.create(type)
                .configure(decoLoader.getConfigMap())
                .parameters(BasicSpecParameter.fromClass(instantiator.loader.getManagementContext(), type)));
        }
    }
    
    public static class InitializerResolver extends BrooklynEntityDecorationResolver<EntityInitializer> {
        
        public InitializerResolver(BrooklynYamlTypeInstantiator.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Entity initializer"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.addInitializers(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_INITIALIZERS);
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<EntityInitializer> decorations) {
            decorations.add(instantiator.from(decorationJson).prefix("initializer").newInstance(EntityInitializer.class));
        }
    }

    // Not much value from extending from BrooklynEntityDecorationResolver, but let's not break the convention
    public static class SpecParameterResolver extends BrooklynEntityDecorationResolver<SpecParameter<?>> {

        protected SpecParameterResolver(BrooklynYamlTypeInstantiator.Factory instantiator) { super(instantiator); }
        @Override protected String getDecorationKind() { return "Spec Parameter initializer"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            List<? extends SpecParameter<?>> explicitParams = buildListOfTheseDecorationsFromEntityAttributes(attrs);
            if (!explicitParams.isEmpty()) {
                entitySpec.parameters(explicitParams);
            }
            if (entitySpec.getParameters().isEmpty()) {
                entitySpec.parameters(BasicSpecParameter.fromSpec(instantiator.loader.getManagementContext(), entitySpec));
            }
        }

        @Override
        protected List<SpecParameter<?>> buildListOfTheseDecorationsFromIterable(Iterable<?> value) {
            return BasicSpecParameter.fromConfigList(ImmutableList.copyOf(value), instantiator.loader);
        }

        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_PARAMETERS);
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<SpecParameter<?>> decorations) {
            throw new IllegalStateException("Not called");
        }
    }

}
