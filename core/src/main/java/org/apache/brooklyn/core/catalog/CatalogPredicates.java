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
package org.apache.brooklyn.core.catalog;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

public class CatalogPredicates {

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> isCatalogItemType(final CatalogItemType ciType) {
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && item.getCatalogItemType()==ciType;
            }
        };
        return new CatalogItemTypeEquals<T, SpecT>(ciType);
    }

    private static class CatalogItemTypeEquals<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final CatalogItemType ciType;
        
        public CatalogItemTypeEquals(final CatalogItemType ciType) {
            this.ciType = ciType;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return (item != null) && item.getCatalogItemType()==ciType;
        }
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> deprecated(final boolean deprecated) {
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && item.isDeprecated() == deprecated;
            }
        };
        return new DeprecatedEquals<T, SpecT>(deprecated);
    }

    private static class DeprecatedEquals<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final boolean deprecated;
        
        public DeprecatedEquals(boolean deprecated) {
            this.deprecated = deprecated;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return (item != null) && item.isDeprecated() == deprecated;
        }
    }

    public static final Predicate<CatalogItem<Application,EntitySpec<? extends Application>>> IS_TEMPLATE = 
            CatalogPredicates.<Application,EntitySpec<? extends Application>>isCatalogItemType(CatalogItemType.TEMPLATE);
    public static final Predicate<CatalogItem<Entity,EntitySpec<?>>> IS_ENTITY = 
            CatalogPredicates.<Entity,EntitySpec<?>>isCatalogItemType(CatalogItemType.ENTITY);
    public static final Predicate<CatalogItem<Policy,PolicySpec<?>>> IS_POLICY = 
            CatalogPredicates.<Policy,PolicySpec<?>>isCatalogItemType(CatalogItemType.POLICY);
    public static final Predicate<CatalogItem<Location,LocationSpec<?>>> IS_LOCATION = 
            CatalogPredicates.<Location,LocationSpec<?>>isCatalogItemType(CatalogItemType.LOCATION);
    
    // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
    @SuppressWarnings("unused")
    private static final Function<CatalogItem<?,?>,String> ID_OF_ITEM_TRANSFORMER_ANONYMOUS = new Function<CatalogItem<?,?>, String>() {
        @Override @Nullable
        public String apply(@Nullable CatalogItem<?,?> input) {
            if (input==null) return null;
            return input.getId();
        }
    };

    // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
    public static final Function<CatalogItem<?,?>,String> ID_OF_ITEM_TRANSFORMER = new IdOfItemTransformer();
    
    private static class IdOfItemTransformer implements Function<CatalogItem<?,?>,String> {
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
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getDisplayName());
            }
        };
        return new DisplayNameMatches<T,SpecT>(filter);
    }

    private static class DisplayNameMatches<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final Predicate<? super String> filter;
        
        public DisplayNameMatches(Predicate<? super String> filter) {
            this.filter = filter;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return (item != null) && filter.apply(item.getDisplayName());
        }
    }

    @Deprecated
    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> registeredTypeName(final Predicate<? super String> filter) {
        return symbolicName(filter);
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> symbolicName(final Predicate<? super String> filter) {
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getSymbolicName());
            }
        };
        return new SymbolicNameMatches<T,SpecT>(filter);
    }
    
    private static class SymbolicNameMatches<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final Predicate<? super String> filter;
        
        public SymbolicNameMatches(Predicate<? super String> filter) {
            this.filter = filter;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return (item != null) && filter.apply(item.getSymbolicName());
        }
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> javaType(final Predicate<? super String> filter) {
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getJavaType());
            }
        };
        return new JavaTypeMatches<T, SpecT>(filter);
    }
    
    private static class JavaTypeMatches<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final Predicate<? super String> filter;
        
        public JavaTypeMatches(Predicate<? super String> filter) {
            this.filter = filter;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return (item != null) && filter.apply(item.getJavaType());
        }
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> xml(final Predicate<? super String> filter) {
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.toXmlString());
            }
        };
        return new XmlMatches<T,SpecT>(filter);
    }
    
    private static class XmlMatches<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final Predicate<? super String> filter;
        
        public XmlMatches(Predicate<? super String> filter) {
            this.filter = filter;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return (item != null) && filter.apply(item.toXmlString());
        }
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> entitledToSee(final ManagementContext mgmt) {
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && 
                    Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_CATALOG_ITEM, item.getCatalogItemId());
            }
        };
        return new EntitledToSee<T, SpecT>(mgmt);
    }
    
    private static class EntitledToSee<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final ManagementContext mgmt;
        
        public EntitledToSee(ManagementContext mgmt) {
            this.mgmt = mgmt;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return (item != null) && 
                    Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_CATALOG_ITEM, item.getCatalogItemId());
        }
    }
 
    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> isBestVersion(final ManagementContext mgmt) {
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return CatalogUtils.isBestVersion(mgmt, item);
            }
        };
        return new IsBestVersion<T, SpecT>(mgmt);
    }
    
    private static class IsBestVersion<T,SpecT> implements Predicate<CatalogItem<T,SpecT>> {
        private final ManagementContext mgmt;
        
        public IsBestVersion(ManagementContext mgmt) {
            this.mgmt = mgmt;
        }
        @Override
        public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
            return CatalogUtils.isBestVersion(mgmt, item);
        }
    }
}