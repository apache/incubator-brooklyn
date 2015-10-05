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
package org.apache.brooklyn.core.mgmt.entitlement;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.mgmt.entitlement.EntitlementClass;
import org.apache.brooklyn.api.mgmt.entitlement.EntitlementManager;

import com.google.common.base.Predicate;

public class EntitlementPredicates {

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<T> isEntitledOld(final EntitlementManager entitlementManager, final EntitlementClass<T> entitlementClass) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<T>() {
            @Override
            public boolean apply(@Nullable T t) {
                return Entitlements.isEntitled(entitlementManager, entitlementClass, t);
            }
        };
    }

    public static <T> Predicate<T> isEntitled(final EntitlementManager entitlementManager, final EntitlementClass<T> entitlementClass) {
        return new IsEntitled<>(checkNotNull(entitlementManager, "entitlementManager"), checkNotNull(entitlementClass, "entitlementClass"));
    }

    protected static class IsEntitled<T> implements Predicate<T> {
        private final EntitlementManager entitlementManager;
        private final EntitlementClass<T> entitlementClass;
        
        protected IsEntitled(final EntitlementManager entitlementManager, final EntitlementClass<T> entitlementClass) {
            this.entitlementManager = checkNotNull(entitlementManager, "entitlementManager");
            this.entitlementClass = checkNotNull(entitlementClass, "entitlementClass");
        }
        @Override
        public boolean apply(@Nullable T t) {
            return Entitlements.isEntitled(entitlementManager, entitlementClass, t);
        }
    }
}
