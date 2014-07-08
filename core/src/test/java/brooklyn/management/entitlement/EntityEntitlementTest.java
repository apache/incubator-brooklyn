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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
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
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;

public class EntityEntitlementTest {

    private static final Logger log = LoggerFactory.getLogger(EntityEntitlementTest.class);
    
    ManagementContext mgmt;
    Application app;
    
    public void setup(ConfigBag cfg) {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty().addFrom(cfg));
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(BasicApplication.class), mgmt);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (mgmt != null) Entities.destroyAll(mgmt);
        app = null;
        mgmt = null;
    }

    @Test
    public void testDefaultRootAllows() {
        setup(ConfigBag.newInstance());
        // default "root" access allows ROOT permission, and invoke effector, etc
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.ROOT, null));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_ENTITY, app));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        // and can invoke methods, without any user/login registered
        confirmEffectorEntitlement(true);
        confirmSensorEntitlement(true);
    }

    @Test
    public void testExplicitRootAllows() {
        setup(ConfigBag.newInstance().configure(Entitlements.GLOBAL_ENTITLEMENT_MANAGER, "root"));
        
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.ROOT, null));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_ENTITY, app));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        
        confirmEffectorEntitlement(true);
        confirmSensorEntitlement(true);
    }

    @Test
    public void testReadOnlyThrowsNotEntitled() {
        try {
            setup(ConfigBag.newInstance().configure(Entitlements.GLOBAL_ENTITLEMENT_MANAGER, "readonly"));
            // eventually above call will fail in app creation, but for now the call below falls
            ((BasicApplication)app).start(ImmutableList.<Location>of());
            tearDown();
            
            Assert.fail("something should have been disallowed");
        } catch (Exception e) {
            checkNotEntitledException(e);
        } finally {
            app = null;
            if (mgmt!=null) ((LocalManagementContext)mgmt).terminate();
            mgmt = null;
        }
    }
    
    @Test(enabled=false)
    // this test (and minimal below) currently work fine until tearDown, 
    // but then of course they don't have entitlement to call 'stop';
    // (as more entitlement checks are added app creation will fail).
    // 
    // TODO these tests should set up a user with the permission,
    // e.g. so system has root, but 'bob' has read-only, then we can test read-only;
    // TODO rest api tests, as some of the permissions will only be enforced at REST level
    public void testReadOnlyAllowsSome() {
        setup(ConfigBag.newInstance().configure(Entitlements.GLOBAL_ENTITLEMENT_MANAGER, "readonly"));
        
        Assert.assertFalse(mgmt.getEntitlementManager().isEntitled(null, Entitlements.ROOT, null));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_ENTITY, app));
        Assert.assertFalse(mgmt.getEntitlementManager().isEntitled(null, Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertTrue(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        
        confirmEffectorEntitlement(false);
        confirmSensorEntitlement(true);
    }
    
    @Test(enabled=false)
    public void testMinimalDisallows() {
        setup(ConfigBag.newInstance().configure(Entitlements.GLOBAL_ENTITLEMENT_MANAGER, "minimal"));
        
        Assert.assertFalse(mgmt.getEntitlementManager().isEntitled(null, Entitlements.ROOT, null));
        Assert.assertFalse(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_ENTITY, app));
        Assert.assertFalse(mgmt.getEntitlementManager().isEntitled(null, Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, "any-eff")));
        Assert.assertFalse(mgmt.getEntitlementManager().isEntitled(null, Entitlements.SEE_SENSOR, EntityAndItem.of(app, "any-sensor")));
        
        confirmEffectorEntitlement(false);
        confirmSensorEntitlement(false);
    }
    
    protected void confirmSensorEntitlement(boolean shouldSucceed) {
        // TODO... based on above
        // (though maybe we should test against REST API classes rather than lower level programmatic?
        // TBC...)
        log.warn("confirmSensorEntitlement still required!");
    }

    // TODO specific users tests
    
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

    private void checkNotEntitledException(Exception e) {
        checkNotEntitledException(false, e);
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
