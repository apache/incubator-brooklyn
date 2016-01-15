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

import org.apache.brooklyn.api.mgmt.entitlement.EntitlementClass;

import com.google.common.base.Objects;
import com.google.common.reflect.TypeToken;


public class BasicEntitlementClassDefinition<T> implements EntitlementClass<T> {

    private final String identifier;
    private final TypeToken<T> argumentType;

    public BasicEntitlementClassDefinition(String identifier, TypeToken<T> argumentType) {
        this.identifier = identifier;
        this.argumentType = argumentType;
    }
    
    public BasicEntitlementClassDefinition(String identifier, Class<T> argumentType) {
        this.identifier = identifier;
        this.argumentType = TypeToken.of(argumentType);
    }
    
    @Override
    public String entitlementClassIdentifier() {
        return identifier;
    }

    @Override
    public TypeToken<T> entitlementClassArgumentType() {
        return argumentType;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("identitifier", identifier).add("argumentType", argumentType).toString();
    }
}
