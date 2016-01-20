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

import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableSet;

class CampResolver {

    private ManagementContext mgmt;
    private RegisteredType type;
    private RegisteredTypeLoadingContext context;

    // TODO we have a few different modes, detailed below; this logic should be moved to the new transformer
    // and allow specifying which modes are permitted to be in effect?
//    /** whether to allow parsing of the 'full' syntax for applications,
//     * where items are wrapped in a "services:" block, and if the wrapper is an application,
//     * to promote it */
//    boolean allowApplicationFullSyntax = true;
//
//    /** whether to allow parsing of the legacy 'full' syntax, 
//     * where a non-application items are wrapped:
//     * <li> in a "services:" block for entities,
//     * <li> in a "brooklyn.locations" or "brooklyn.policies" block for locations and policies */
//    boolean allowLegacyFullSyntax = true;
//
//    /** whether to allow parsing of the type syntax, where an item is a map with a "type:" field,
//     * i.e. not wrapped in any "services:" or "brooklyn.{locations,policies}" block */
//    boolean allowTypeSyntax = true;

    public CampResolver(ManagementContext mgmt, RegisteredType type, RegisteredTypeLoadingContext context) {
        this.mgmt = mgmt;
        this.type = type;
        this.context = context;
    }

    public AbstractBrooklynObjectSpec<?, ?> createSpec() {
        // TODO new-style approach:
        //            AbstractBrooklynObjectSpec<?, ?> spec = RegisteredTypes.newSpecInstance(mgmt, /* 'type' key */);
        //            spec.configure(keysAndValues);
        return createSpecFromFull(mgmt, type, context.getExpectedJavaSuperType(), context.getAlreadyEncounteredTypes(), context.getLoader());
    }

    static AbstractBrooklynObjectSpec<?, ?> createSpecFromFull(ManagementContext mgmt, RegisteredType item, Class<?> expectedType, Set<String> parentEncounteredTypes, BrooklynClassLoadingContext loaderO) {
        // for this method, a prefix "services" or "brooklyn.{location,policies}" is required at the root;
        // we now prefer items to come in "{ type: .. }" format, except for application roots which
        // should have a "services: [ ... ]" block (and which may subsequently be unwrapped)
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, item, loaderO);

        Set<String> encounteredTypes;
        // symbolicName could be null if coming from the catalog parser where it tries to load before knowing the id
        if (item.getSymbolicName() != null) {
            encounteredTypes = ImmutableSet.<String>builder()
                .addAll(parentEncounteredTypes)
                .add(item.getSymbolicName())
                .build();
        } else {
            encounteredTypes = parentEncounteredTypes;
        }

        AbstractBrooklynObjectSpec<?, ?> spec;
        String planYaml = RegisteredTypes.getImplementationDataStringForSpec(item);
        MutableSet<Object> supers = MutableSet.copyOf(item.getSuperTypes());
        supers.addIfNotNull(expectedType);
        if (RegisteredTypes.isAnyTypeSubtypeOf(supers, Policy.class)) {
            spec = CampInternalUtils.createPolicySpec(planYaml, loader, encounteredTypes);
        } else if (RegisteredTypes.isAnyTypeSubtypeOf(supers, Location.class)) {
            spec = CampInternalUtils.createLocationSpec(planYaml, loader, encounteredTypes);
        } else if (RegisteredTypes.isAnyTypeSubtypeOf(supers, Application.class)) {
            spec = createEntitySpecFromServicesBlock(planYaml, loader, encounteredTypes, true);
        } else if (RegisteredTypes.isAnyTypeSubtypeOf(supers, Entity.class)) {
            spec = createEntitySpecFromServicesBlock(planYaml, loader, encounteredTypes, false);
        } else {
            throw new IllegalStateException("Cannot detect spec type from "+item.getSuperTypes()+" for "+item+"\n"+planYaml);
        }
        if (expectedType!=null && !expectedType.isAssignableFrom(spec.getType())) {
            throw new IllegalStateException("Creating spec from "+item+", got "+spec.getType()+" which is incompatible with expected "+expectedType);                
        }

        ((AbstractBrooklynObjectSpec<?, ?>)spec).catalogItemIdIfNotNull(item.getId());

        if (Strings.isBlank( ((AbstractBrooklynObjectSpec<?, ?>)spec).getDisplayName() ))
            ((AbstractBrooklynObjectSpec<?, ?>)spec).displayName(item.getDisplayName());

        return spec;
    }
 
    private static EntitySpec<?> createEntitySpecFromServicesBlock(String plan, BrooklynClassLoadingContext loader, Set<String> encounteredTypes, boolean isApplication) {
        CampPlatform camp = CampInternalUtils.getCampPlatform(loader.getManagementContext());

        AssemblyTemplate at = CampInternalUtils.registerDeploymentPlan(plan, loader, camp);
        AssemblyTemplateInstantiator instantiator = CampInternalUtils.getInstantiator(at);
        if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
            EntitySpec<? extends Application> appSpec = ((AssemblyTemplateSpecInstantiator)instantiator).createApplicationSpec(at, camp, loader, encounteredTypes);

            // above will unwrap but only if it's an Application (and it's permitted); 
            // but it doesn't know whether we need an App or if an Entity is okay  
            if (!isApplication) return EntityManagementUtils.unwrapEntity(appSpec);
            // if we need an App then definitely *don't* unwrap here because
            // the instantiator will have done that, and it knows if the plan
            // specified a wrapped app explicitly (whereas we don't easily know that here!)
            return appSpec;
            
        } else {
            throw new IllegalStateException("Unable to instantiate YAML; invalid type or parameters in plan:\n"+plan);
        }

    }

}