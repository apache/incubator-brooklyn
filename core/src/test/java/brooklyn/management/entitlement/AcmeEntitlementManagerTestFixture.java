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

import java.io.IOException;
import java.net.URI;

import org.apache.brooklyn.management.ManagementContext;
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
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.entitlement.NotEntitledException;
import brooklyn.management.entitlement.WebEntitlementContext;
import brooklyn.management.entitlement.Entitlements.EntityAndItem;
import brooklyn.management.entitlement.Entitlements.StringAndArgument;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;

public abstract class AcmeEntitlementManagerTestFixture {

    protected ManagementContext mgmt;
    protected Application app;
    protected ConfigBag configBag;
    
    public void setup(ConfigBag cfg) {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty().addFrom(cfg));
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(BasicApplication.class), mgmt);
    }

    @BeforeMethod(alwaysRun=true)
    public void init() throws IOException {
        Entitlements.clearEntitlementContext();
        configBag = ConfigBag.newInstance();
        addGlobalConfig();
    }

    protected abstract void addGlobalConfig() throws IOException;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        Entitlements.clearEntitlementContext();
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (mgmt != null) Entities.destroyAll(mgmt);
        app = null;
        mgmt = null;
    }

    @Test
    public void testMetricsHasMinimalPermissions() {
        checkUserHasMinimalPermissions("metrics");
    }
    
    public void checkUserHasMinimalPermissions(String username) {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext(username, "127.0.0.1", URI.create("/applications").toString(), "H");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, StringAndArgument.of("any-eff", null))));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and can invoke methods
        confirmEffectorEntitlement(false);
    }

    @Test
    public void testSupportHasReadOnlyPermissions() {
        checkUserHasReadOnlyPermissions("support");
    }
    
    public void checkUserHasReadOnlyPermissions(String username) {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext(username, "127.0.0.1", URI.create("/X").toString(), "B");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertFalse(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, StringAndArgument.of("any-eff", null))));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and cannot invoke methods
        confirmEffectorEntitlement(false);
    }

    @Test
    public void testAdminHasAllPermissions() {
        checkUserHasAllPermissions("admin");
    }
    
    public void checkUserHasAllPermissions(String user) {
        setup(configBag);
        WebEntitlementContext entitlementContext = new WebEntitlementContext(user, "127.0.0.1", URI.create("/X").toString(), "A");
        Entitlements.setEntitlementContext(entitlementContext);
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.ROOT, null));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, app));
        Assert.assertTrue(Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, StringAndArgument.of("any-eff", null))));
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

