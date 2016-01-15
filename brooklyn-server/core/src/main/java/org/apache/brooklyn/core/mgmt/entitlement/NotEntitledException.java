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
import org.apache.brooklyn.api.mgmt.entitlement.EntitlementContext;


public class NotEntitledException extends RuntimeException {

    private static final long serialVersionUID = -4001882260980589181L;
    
    EntitlementContext entitlementContext;
    EntitlementClass<?> permission;
    Object typeArgument;
    
    public <T> NotEntitledException(EntitlementContext entitlementContext, EntitlementClass<T> permission, T typeArgument) {
        this.entitlementContext = entitlementContext;
        this.permission = permission;
        this.typeArgument = typeArgument;
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+entitlementContext+":"+permission+(typeArgument!=null ? "("+typeArgument+")" : "")+"]";
    }

}
