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
package brooklyn.management.entitlement;

import org.apache.brooklyn.management.entitlement.EntitlementClass;
import org.apache.brooklyn.management.entitlement.EntitlementContext;
import org.apache.brooklyn.management.entitlement.EntitlementManager;

public class AcmeEntitlementManager extends PerUserEntitlementManager {

    public AcmeEntitlementManager() {
        // default mode (if no user specified) is root
        super(Entitlements.root());
        
        super.addUser("admin", Entitlements.root());
        
        super.addUser("support", Entitlements.readOnly());
        
        // metrics can log in but can't really do much else
        super.addUser("metrics", Entitlements.minimal());
        
        // 'navigator' defines a user with a custom entitlement manager allowing 
        // access to see entities (in the tree) but not to do anything 
        // or even see any sensor information on those entities
        super.addUser("navigator", new EntitlementManager() {
            @Override
            public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> entitlementClass, T entitlementClassArgument) {
                if (Entitlements.SEE_ENTITY.equals(entitlementClass)) return true;
                return false;
            }
        });
    }

}
