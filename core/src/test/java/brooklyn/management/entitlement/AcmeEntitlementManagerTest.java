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

import java.net.URI;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.entitlement.Entitlements.EntityAndItem;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;

public class AcmeEntitlementManagerTest {

    ManagementContext mgmt;
    Application app;
    ConfigBag configBag;
    
    public void setup(ConfigBag cfg) {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty().addFrom(cfg));
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(BasicApplication.class), mgmt);
    }

    @BeforeMethod(alwaysRun=true)
    public void init() {
        configBag = ConfigBag.newInstance();
        configBag.put(Entitlements.GLOBAL_ENTITLEMENT_MANAGER, AcmeEntitlementManager.class.getName());
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (mgmt != null) Entities.destroyAll(mgmt);
        app = null;
        mgmt = null;
    }

    @Test
    public void testUserWithMinimal() {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext("hacker", "127.0.0.1", URI.create("/applications").toString(), "H");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and can invoke methods
        confirmEffectorEntitlement(false);
    }

    @Test
    public void testUserWithReadOnly() {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext("bob", "127.0.0.1", URI.create("/applications").toString(), "B");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and cannot invoke methods
        confirmEffectorEntitlement(false);
    }

    @Test
    public void testUserWithAllPermissions() {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext("alice", "127.0.0.1", URI.create("/applications").toString(), "A");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and can invoke methods
        confirmEffectorEntitlement(true);
    }

    @Test
    public void testNullHasAllPermissions() {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext(null, "127.0.0.1", URI.create("/applications").toString(), "X");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and can invoke methods
        confirmEffectorEntitlement(true);
    }

    protected void confirmEffectorEntitlement(boolean shouldSucceed) {
        try {
            ((BasicApplication)app).start(ImmutableList.<Location>of());
            checkNoException(shouldSucceed);
        } catch (Exception e) {
            checkNotEntitledException(shouldSucceed, e);
        }
    }

    private void checkNoException(boolean shouldBeEntitled) {
        checkNotEntitledException(shouldBeEntitled, null);
    }

    private void checkNotEntitledException(boolean shouldBeEntitled, Exception e) {
        if (e==null) {
            if (shouldBeEntitled) return;
            Assert.fail("entitlement should have been denied");
        }
        
        Exception notEntitled = Exceptions.getFirstThrowableOfType(e, NotEntitledException.class);
        if (notEntitled==null)
            throw Exceptions.propagate(e);
        if (!shouldBeEntitled) {
            /* denied, as it should have been */ 
            return;
        }
        Assert.fail("entitlement should have been granted, but was denied: "+e);
    }

}

