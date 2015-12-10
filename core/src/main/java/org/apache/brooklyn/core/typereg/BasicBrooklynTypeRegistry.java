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
package org.apache.brooklyn.core.typereg;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredType.TypeImplementationPlan;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.catalog.internal.CatalogItemBuilder;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Preconditions;
import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class BasicBrooklynTypeRegistry implements BrooklynTypeRegistry {

    private static final Logger log = LoggerFactory.getLogger(BasicBrooklynTypeRegistry.class);
    
    private ManagementContext mgmt;
    private Map<String,RegisteredType> localRegisteredTypes = MutableMap.of();

    public BasicBrooklynTypeRegistry(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    public Iterable<RegisteredType> getAll() {
        return getMatching(Predicates.alwaysTrue());
    }
    
    private Iterable<RegisteredType> getAllWithoutCatalog(Predicate<? super RegisteredType> filter) {
        // TODO thread safety
        // TODO optimisation? make indexes and look up?
        return Iterables.filter(localRegisteredTypes.values(), filter);
    }

    private RegisteredType getExactWithoutLegacyCatalog(String symbolicName, String version, RegisteredTypeLoadingContext constraint) {
        // TODO look in any nested/private registries
        RegisteredType item = localRegisteredTypes.get(symbolicName+":"+version);
        return RegisteredTypes.tryValidate(item, constraint).orNull();
    }

    @SuppressWarnings("deprecation")
    @Override
    public Iterable<RegisteredType> getMatching(Predicate<? super RegisteredType> filter) {
        return Iterables.filter(Iterables.concat(
                getAllWithoutCatalog(filter),
                Iterables.transform(mgmt.getCatalog().getCatalogItems(), RegisteredTypes.CI_TO_RT)), 
            filter);
    }

    @SuppressWarnings("deprecation")
    private Maybe<RegisteredType> getSingle(String symbolicNameOrAliasIfNoVersion, final String versionFinal, final RegisteredTypeLoadingContext contextFinal) {
        RegisteredTypeLoadingContext context = contextFinal;
        if (context==null) context = RegisteredTypeLoadingContexts.any();
        String version = versionFinal;
        if (version==null) version = BrooklynCatalog.DEFAULT_VERSION;

        RegisteredType type;
        if (!BrooklynCatalog.DEFAULT_VERSION.equals(version)) {
            // normal code path when version is supplied
            
            type = getExactWithoutLegacyCatalog(symbolicNameOrAliasIfNoVersion, version, context);
            if (type!=null) return Maybe.of(type);
        }

        if (BrooklynCatalog.DEFAULT_VERSION.equals(version)) {
            // alternate code path, if version blank or default
            
            Iterable<RegisteredType> types = getMatching(Predicates.and(RegisteredTypePredicates.symbolicName(symbolicNameOrAliasIfNoVersion), 
                RegisteredTypePredicates.satisfies(context)));
            if (Iterables.isEmpty(types)) {
                // look for alias if no exact symbolic name match AND no version is specified
                types = getMatching(Predicates.and(RegisteredTypePredicates.alias(symbolicNameOrAliasIfNoVersion), 
                    RegisteredTypePredicates.satisfies(context) ) );
                // if there are multiple symbolic names then throw?
                Set<String> uniqueSymbolicNames = MutableSet.of();
                for (RegisteredType t: types) {
                    uniqueSymbolicNames.add(t.getSymbolicName());
                }
                if (uniqueSymbolicNames.size()>1) {
                    String message = "Multiple matches found for alias '"+symbolicNameOrAliasIfNoVersion+"': "+uniqueSymbolicNames+"; "
                        + "refusing to select any.";
                    log.warn(message);
                    return Maybe.absent(message);
                }
            }
            if (!Iterables.isEmpty(types)) {
                type = RegisteredTypes.getBestVersion(types);
                if (type!=null) return Maybe.of(type);
            }
        }
        
        // missing case is to look for exact version in legacy catalog
        CatalogItem<?, ?> item = mgmt.getCatalog().getCatalogItem(symbolicNameOrAliasIfNoVersion, version);
        if (item!=null) 
            return Maybe.of( RegisteredTypes.CI_TO_RT.apply( item ) );
        
        return Maybe.absent("No matches for "+symbolicNameOrAliasIfNoVersion+
            (versionFinal!=null ? ":"+versionFinal : "")+
            (contextFinal!=null ? " ("+contextFinal+")" : "") );
    }

    @Override
    public RegisteredType get(String symbolicName, String version) {
        return getSingle(symbolicName, version, null).orNull();
    }
    
    @Override
    public RegisteredType get(String symbolicNameWithOptionalVersion, RegisteredTypeLoadingContext context) {
        return getMaybe(symbolicNameWithOptionalVersion, context).orNull();
    }
    @Override
    public Maybe<RegisteredType> getMaybe(String symbolicNameWithOptionalVersion, RegisteredTypeLoadingContext context) {
        Maybe<RegisteredType> r1 = null;
        if (CatalogUtils.looksLikeVersionedId(symbolicNameWithOptionalVersion)) {
            String symbolicName = CatalogUtils.getSymbolicNameFromVersionedId(symbolicNameWithOptionalVersion);
            String version = CatalogUtils.getVersionFromVersionedId(symbolicNameWithOptionalVersion);
            r1 = getSingle(symbolicName, version, context);
            if (r1.isPresent()) return r1;
        }

        Maybe<RegisteredType> r2 = getSingle(symbolicNameWithOptionalVersion, BrooklynCatalog.DEFAULT_VERSION, context);
        if (r2.isPresent() || r1==null) return r2;
        return r1;
    }

    @Override
    public RegisteredType get(String symbolicNameWithOptionalVersion) {
        return get(symbolicNameWithOptionalVersion, (RegisteredTypeLoadingContext)null);
    }

    @Override
    public <SpecT extends AbstractBrooklynObjectSpec<?,?>> SpecT createSpec(RegisteredType type, @Nullable RegisteredTypeLoadingContext constraint, Class<SpecT> specSuperType) {
        Preconditions.checkNotNull(type, "type");
        if (type.getKind()!=RegisteredTypeKind.SPEC) { 
            throw new IllegalStateException("Cannot create spec from type "+type+" (kind "+type.getKind()+")");
        }
        return createSpec(type, type.getPlan(), type.getSymbolicName(), type.getVersion(), type.getSuperTypes(), constraint, specSuperType);
    }
    
    @SuppressWarnings({ "deprecation", "unchecked", "rawtypes" })
    private <SpecT extends AbstractBrooklynObjectSpec<?,?>> SpecT createSpec(
            RegisteredType type,
            TypeImplementationPlan plan,
            @Nullable String symbolicName, @Nullable String version, Set<Object> superTypes,
            @Nullable RegisteredTypeLoadingContext constraint, Class<SpecT> specSuperType) {
        // TODO type is only used to call to "transform"; we should perhaps change transform so it doesn't need the type?
        if (constraint!=null) {
            if (constraint.getExpectedKind()!=null && constraint.getExpectedKind()!=RegisteredTypeKind.SPEC) {
                throw new IllegalStateException("Cannot create spec with constraint "+constraint);
            }
            if (constraint.getAlreadyEncounteredTypes().contains(symbolicName)) {
                // avoid recursive cycle
                // TODO implement using java if permitted
            }
        }
        constraint = RegisteredTypeLoadingContexts.withSpecSuperType(constraint, specSuperType);

        Maybe<Object> result = TypePlanTransformers.transform(mgmt, type, constraint);
        if (result.isPresent()) return (SpecT) result.get();
        
        // fallback: look up in (legacy) catalog
        // TODO remove once all transformers are available in the new style
        CatalogItem item = symbolicName!=null ? (CatalogItem) mgmt.getCatalog().getCatalogItem(symbolicName, version) : null;
        if (item==null) {
            // if not in catalog (because loading a new item?) then look up item based on type
            // (only really used in tests; possibly also for any recursive legacy transformers we might have to create a CI; cross that bridge when we come to it)
            CatalogItemType ciType = CatalogItemType.ofTargetClass( (Class)constraint.getExpectedJavaSuperType() );
            if (ciType==null) {
                // throw -- not supported for non-spec types
                result.get();
            }
            item = CatalogItemBuilder.newItem(ciType, 
                    symbolicName!=null ? symbolicName : Identifiers.makeRandomId(8), 
                        version!=null ? version : BasicBrooklynCatalog.DEFAULT_VERSION)
                .plan((String)plan.getPlanData())
                .build();
        }
        try {
            return (SpecT) BasicBrooklynCatalog.internalCreateSpecLegacy(mgmt, item, constraint.getAlreadyEncounteredTypes(), false);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            // for now, combine this failure with the original
            try {
                result.get();
                // above will throw -- so won't come here
                throw new IllegalStateException("should have failed getting type resolution for "+symbolicName);
            } catch (Exception e0) {
                Set<Exception> exceptionsInOrder = MutableSet.of();
                if (e0.toString().indexOf("none of the available transformers")>=0) {
                    // put the legacy exception first if none of the new transformers support the type
                    // (until the new transformer is the primary pathway)
                    exceptionsInOrder.add(e);
                }
                exceptionsInOrder.add(e0);
                exceptionsInOrder.add(e);
                throw Exceptions.create("Unable to instantiate "+(symbolicName==null ? "item" : symbolicName), exceptionsInOrder); 
            }
        }
    }

    @Override
    public <SpecT extends AbstractBrooklynObjectSpec<?, ?>> SpecT createSpecFromPlan(String planFormat, Object planData, RegisteredTypeLoadingContext optionalConstraint, Class<SpecT> optionalSpecSuperType) {
        return createSpec(RegisteredTypes.spec(null, null, new BasicTypeImplementationPlan(planFormat, planData), null),
            optionalConstraint, optionalSpecSuperType);
    }

    @Override
    public <T> T createBean(RegisteredType type, RegisteredTypeLoadingContext constraint, Class<T> optionalResultSuperType) {
        Preconditions.checkNotNull(type, "type");
        if (type.getKind()!=RegisteredTypeKind.SPEC) { 
            throw new IllegalStateException("Cannot create spec from type "+type+" (kind "+type.getKind()+")");
        }
        if (constraint!=null) {
            if (constraint.getExpectedKind()!=null && constraint.getExpectedKind()!=RegisteredTypeKind.SPEC) {
                throw new IllegalStateException("Cannot create spec with constraint "+constraint);
            }
            if (constraint.getAlreadyEncounteredTypes().contains(type.getSymbolicName())) {
                // avoid recursive cycle
                // TODO create type using java if permitted?
                // OR remove this creator from those permitted
            }
        }
        constraint = RegisteredTypeLoadingContexts.withBeanSuperType(constraint, optionalResultSuperType);

        @SuppressWarnings("unchecked")
        T result = (T) TypePlanTransformers.transform(mgmt, type, constraint).get();
        return result;
    }

    @Override
    public <T> T createBeanFromPlan(String planFormat, Object planData, RegisteredTypeLoadingContext optionalConstraint, Class<T> optionalBeanSuperType) {
        return createBean(RegisteredTypes.bean(null, null, new BasicTypeImplementationPlan(planFormat, planData), null),
            optionalConstraint, optionalBeanSuperType);
    }

    @Beta // API is stabilising
    public void addToLocalUnpersistedTypeRegistry(RegisteredType type, boolean canForce) {
        Preconditions.checkNotNull(type);
        Preconditions.checkNotNull(type.getSymbolicName());
        Preconditions.checkNotNull(type.getVersion());
        Preconditions.checkNotNull(type.getId());
        if (!type.getId().equals(type.getSymbolicName()+":"+type.getVersion()))
            Asserts.fail("Registered type "+type+" has ID / symname mismatch");
        
        RegisteredType oldType = mgmt.getTypeRegistry().get(type.getId());
        if (oldType==null || canForce) {
            log.debug("Inserting "+type+" into "+this);
            localRegisteredTypes.put(type.getId(), type);
        } else {
            if (oldType == type) {
                // ignore if same instance
                // (equals not yet implemented, so would be the same, but misleading)
                return;
            }
            throw new IllegalStateException("Cannot add "+type+" to catalog; different "+oldType+" is already present");
        }
    }
}
