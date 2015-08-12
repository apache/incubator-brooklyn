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
package brooklyn.catalog;

import javax.annotation.Nullable;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.policy.Policy;
import org.apache.brooklyn.policy.PolicySpec;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.entitlement.Entitlements;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

public class CatalogPredicates {

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> isCatalogItemType(final CatalogItemType ciType) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && item.getCatalogItemType()==ciType;
            }
        };
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> deprecated(final boolean deprecated) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && item.isDeprecated() == deprecated;
            }
        };
    }

    public static final Predicate<CatalogItem<Application,EntitySpec<? extends Application>>> IS_TEMPLATE = 
            CatalogPredicates.<Application,EntitySpec<? extends Application>>isCatalogItemType(CatalogItemType.TEMPLATE);
    public static final Predicate<CatalogItem<Entity,EntitySpec<?>>> IS_ENTITY = 
            CatalogPredicates.<Entity,EntitySpec<?>>isCatalogItemType(CatalogItemType.ENTITY);
    public static final Predicate<CatalogItem<Policy,PolicySpec<?>>> IS_POLICY = 
            CatalogPredicates.<Policy,PolicySpec<?>>isCatalogItemType(CatalogItemType.POLICY);
    public static final Predicate<CatalogItem<Location,LocationSpec<?>>> IS_LOCATION = 
            CatalogPredicates.<Location,LocationSpec<?>>isCatalogItemType(CatalogItemType.LOCATION);
    
    public static final Function<CatalogItem<?,?>,String> ID_OF_ITEM_TRANSFORMER = new Function<CatalogItem<?,?>, String>() {
        @Override @Nullable
        public String apply(@Nullable CatalogItem<?,?> input) {
            if (input==null) return null;
            return input.getId();
        }
    };

    /** @deprecated since 0.7.0 use {@link #displayName(Predicate)} */
    @Deprecated
    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> name(final Predicate<? super String> filter) {
        return displayName(filter);
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> displayName(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getDisplayName());
            }
        };
    }

    @Deprecated
    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> registeredTypeName(final Predicate<? super String> filter) {
        return symbolicName(filter);
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> symbolicName(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getSymbolicName());
            }
        };
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> javaType(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getJavaType());
            }
        };
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> xml(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.toXmlString());
            }
        };
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> entitledToSee(final ManagementContext mgmt) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && 
                    Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_CATALOG_ITEM, item.getCatalogItemId());
            }
        };
    }
 
    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> isBestVersion(final ManagementContext mgmt) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return CatalogUtils.isBestVersion(mgmt, item);
            }
        };
    }

}