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

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeConstraint;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class BasicBrooklynTypeRegistry implements BrooklynTypeRegistry {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BasicBrooklynTypeRegistry.class);
    
    private ManagementContext mgmt;

    public BasicBrooklynTypeRegistry(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    public Iterable<RegisteredType> getAll() {
        return getAll(Predicates.alwaysTrue());
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public Iterable<RegisteredType> getAll(Predicate<? super RegisteredType> filter) {
        return Iterables.filter(Iterables.transform(mgmt.getCatalog().getCatalogItems(), RegisteredTypes.CI_TO_RT), filter);
    }

    @SuppressWarnings("deprecation")
    @Override
    public RegisteredType get(String symbolicName, String version, RegisteredTypeConstraint constraint) {
        if (constraint==null) constraint = RegisteredTypeConstraints.any();
        if (version==null) version = BrooklynCatalog.DEFAULT_VERSION;
        
        // TODO lookup here, using constraints
        
        // fallback to catalog
        CatalogItem<?, ?> item = mgmt.getCatalog().getCatalogItem(symbolicName, version);
        // TODO apply constraint
        return RegisteredTypes.CI_TO_RT.apply( item );
    }

    @Override
    public RegisteredType get(String symbolicName, String version) {
        return get(symbolicName, version, null);
    }
    
    @Override
    public RegisteredType get(String symbolicNameWithOptionalVersion, RegisteredTypeConstraint constraint) {
        if (CatalogUtils.looksLikeVersionedId(symbolicNameWithOptionalVersion)) {
            String symbolicName = CatalogUtils.getSymbolicNameFromVersionedId(symbolicNameWithOptionalVersion);
            String version = CatalogUtils.getVersionFromVersionedId(symbolicNameWithOptionalVersion);
            return get(symbolicName, version, constraint);
        } else {
            return get(symbolicNameWithOptionalVersion, BrooklynCatalog.DEFAULT_VERSION, constraint);
        }
    }

    @Override
    public RegisteredType get(String symbolicNameWithOptionalVersion) {
        return get(symbolicNameWithOptionalVersion, (RegisteredTypeConstraint)null);
    }

    @SuppressWarnings({ "deprecation", "unchecked", "rawtypes" })
    @Override
    public <SpecT extends AbstractBrooklynObjectSpec<?,?>> SpecT createSpec(RegisteredType type, @Nullable RegisteredTypeConstraint constraint, Class<SpecT> specSuperType) {
        Preconditions.checkNotNull(type, "type");
        if (type.getKind()!=RegisteredTypeKind.SPEC) { 
            throw new IllegalStateException("Cannot create spec from type "+type+" (kind "+type.getKind()+")");
        }
        if (constraint!=null) {
            if (constraint.getKind()!=null && constraint.getKind()!=RegisteredTypeKind.SPEC) {
                throw new IllegalStateException("Cannot create spec with constraint "+constraint);
            }
            if (constraint.getEncounteredTypes().contains(type.getSymbolicName())) {
                // avoid recursive cycle
                // TODO implement using java if permitted
            }
        }
        constraint = RegisteredTypeConstraints.withSpecSuperType(constraint, specSuperType);

        Maybe<Object> result = TypePlanTransformers.transform(mgmt, type, constraint);
        if (result.isPresent()) return (SpecT) result.get();
        
        // fallback: look up in (legacy) catalog
        // TODO remove once all transformers are available in the new style
        CatalogItem item = (CatalogItem) mgmt.getCatalog().getCatalogItem(type.getSymbolicName(), type.getVersion());
        if (item==null) {
            // if not in catalog (because loading new one?) then throw original
            result.get();
        }
        try {
            return (SpecT) BasicBrooklynCatalog.internalCreateSpecWithTransformers(mgmt, item, constraint.getEncounteredTypes());
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            // for now, combine this failure with the original
            try {
                result.get();
                // above will throw -- so won't come here
                throw new IllegalStateException("should have failed getting type resolution for "+type);
            } catch (Exception e0) {
                // prefer older exception, until the new transformer is the primary pathway
                throw Exceptions.create("Unable to instantiate "+type, MutableList.of(e, e0));
            }
            // ultimately swallow the legacy failure, return the original failure (the call below will throw because result is absent)
//            return (SpecT) result.get();
        }
    }

}
