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
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class RegisteredTypePredicates {

    public static Predicate<RegisteredType> deprecated(final boolean deprecated) {
        return new DeprecatedEqualTo(deprecated);
    }

    private static class DeprecatedEqualTo implements Predicate<RegisteredType> {
        private final boolean deprecated;
        
        public DeprecatedEqualTo(boolean deprecated) {
            this.deprecated = deprecated;
        }
        @Override
        public boolean apply(@Nullable RegisteredType item) {
            return (item != null) && item.isDeprecated() == deprecated;
        }
    }

    public static Predicate<RegisteredType> disabled(boolean disabled) {
        return new DisabledEqualTo(disabled);
    }

    private static class DisabledEqualTo implements Predicate<RegisteredType> {
        private final boolean disabled;
        
        public DisabledEqualTo(boolean disabled) {
            this.disabled = disabled;
        }
        @Override
        public boolean apply(@Nullable RegisteredType item) {
            return (item != null) && item.isDisabled() == disabled;
        }
    }

    public static final Function<RegisteredType,String> ID_OF_ITEM_TRANSFORMER = new IdOfItemTransformer();
    
    private static class IdOfItemTransformer implements Function<RegisteredType,String> {
        @Override @Nullable
        public String apply(@Nullable RegisteredType input) {
            if (input==null) return null;
            return input.getId();
        }
    };

    public static Predicate<RegisteredType> displayName(final Predicate<? super String> filter) {
        return new DisplayNameMatches(filter);
    }

    private static class DisplayNameMatches implements Predicate<RegisteredType> {
        private final Predicate<? super String> filter;
        
        public DisplayNameMatches(Predicate<? super String> filter) {
            this.filter = filter;
        }
        @Override
        public boolean apply(@Nullable RegisteredType item) {
            return (item != null) && filter.apply(item.getDisplayName());
        }
    }

    public static Predicate<RegisteredType> symbolicName(final Predicate<? super String> filter) {
        return new SymbolicNameMatches(filter);
    }
    
    private static class SymbolicNameMatches implements Predicate<RegisteredType> {
        private final Predicate<? super String> filter;
        
        public SymbolicNameMatches(Predicate<? super String> filter) {
            this.filter = filter;
        }
        @Override
        public boolean apply(@Nullable RegisteredType item) {
            return (item != null) && filter.apply(item.getSymbolicName());
        }
    }

    public static <T> Predicate<RegisteredType> anySuperType(final Predicate<Class<T>> filter) {
        return new AnySuperTypeMatches(filter);
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Predicate<RegisteredType> assignableFrom(final Class<?> filter) {
        return anySuperType((Predicate)Predicates.assignableFrom(filter));
    }
    
    private static class AnySuperTypeMatches implements Predicate<RegisteredType> {
        private final Predicate<Class<?>> filter;
        
        @SuppressWarnings({ "rawtypes", "unchecked" })
        private AnySuperTypeMatches(Predicate filter) {
            this.filter = filter;
        }
        @Override
        public boolean apply(@Nullable RegisteredType item) {
            if (item==null) return false;
            return RegisteredTypes.isAnyTypeOrSuperSatisfying(item.getSuperTypes(), filter);
        }
    }

    public static final Predicate<RegisteredType> IS_APPLICATION = assignableFrom(Application.class);
    public static final Predicate<RegisteredType> IS_ENTITY = assignableFrom(Entity.class);
    public static final Predicate<RegisteredType> IS_LOCATION = assignableFrom(Location.class);
    public static final Predicate<RegisteredType> IS_POLICY = assignableFrom(Policy.class);

    public static Predicate<RegisteredType> entitledToSee(final ManagementContext mgmt) {
        return new EntitledToSee(mgmt);
    }
    
    private static class EntitledToSee implements Predicate<RegisteredType> {
        private final ManagementContext mgmt;
        
        public EntitledToSee(ManagementContext mgmt) {
            this.mgmt = mgmt;
        }
        @Override
        public boolean apply(@Nullable RegisteredType item) {
            return (item != null) && 
                    Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_CATALOG_ITEM, item.getId());
        }
    }
 
    public static Predicate<RegisteredType> isBestVersion(final ManagementContext mgmt) {
        return new IsBestVersion(mgmt);
    }

    private static class IsBestVersion implements Predicate<RegisteredType> {
        private final ManagementContext mgmt;

        public IsBestVersion(ManagementContext mgmt) {
            this.mgmt = mgmt;
        }
        @Override
        public boolean apply(@Nullable RegisteredType item) {
            return isBestVersion(mgmt, item);
        }
    }
 
    public static boolean isBestVersion(ManagementContext mgmt, RegisteredType item) {
        RegisteredType bestVersion = mgmt.getTypeRegistry().get(item.getSymbolicName(), BrooklynCatalog.DEFAULT_VERSION);
        if (bestVersion==null) return false;
        return (bestVersion.getVersion().equals(item.getVersion()));
    }

}