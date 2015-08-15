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
package org.apache.brooklyn.core.management.entitlement;

import java.net.URI;

import org.apache.brooklyn.core.management.entitlement.Entitlements;
import org.apache.brooklyn.core.management.entitlement.WebEntitlementContext;
import org.apache.brooklyn.core.management.entitlement.Entitlements.EntityAndItem;
import org.apache.brooklyn.core.management.entitlement.Entitlements.StringAndArgument;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AcmeEntitlementManagerTest extends AcmeEntitlementManagerTestFixture {

    protected void addGlobalConfig() {
        configBag.put(Entitlements.GLOBAL_ENTITLEMENT_MANAGER, AcmeEntitlementManager.class.getName());
    }

    @Test
    public void testOtherAuthorizedUserHasAllPermissions() {
        checkUserHasAllPermissions("other");
    }

    @Test
    public void testNullUserHasAllPermissions() {
        checkUserHasAllPermissions(null);
    }

    @Test
    public void testNavigatorHasListPermissionsOnly() {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext("navigator", "127.0.0.1", URI.create("/X").toString(), "X");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, StringAndArgument.of("any-eff", null))));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and cannot invoke methods
        confirmEffectorEntitlement(false);
    }

}

