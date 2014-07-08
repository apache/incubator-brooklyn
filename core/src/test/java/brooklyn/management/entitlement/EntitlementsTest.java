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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;

@Test
public class EntitlementsTest {

    private ManagementContext mgmt;
    private Application app;

    @BeforeMethod
    public void setup() {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty());
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(BasicApplication.class), mgmt);
    }

    @AfterMethod
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (mgmt != null) Entities.destroyAll(mgmt);
        app = null;
        mgmt = null;
    }

    // allowing
    public void testAllowingRoot() {
        EntitlementManager allowSeeEntity = Entitlements.FineGrainedEntitlements.allowing(Entitlements.ROOT);
        assertTrue(allowSeeEntity.isEntitled(null, Entitlements.ROOT, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAllowingSeeEntity() {
        EntitlementManager allowSeeEntity = Entitlements.FineGrainedEntitlements.allowing(Entitlements.SEE_ENTITY);
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.ROOT, null));
        assertTrue(allowSeeEntity.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAllowingInvokeEffector() {
        EntitlementManager allowSeeEntity = Entitlements.FineGrainedEntitlements.allowing(Entitlements.INVOKE_EFFECTOR);
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.ROOT, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertTrue(allowSeeEntity.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAllowingSeeSensor() {
        EntitlementManager allowSeeEntity = Entitlements.FineGrainedEntitlements.allowing(Entitlements.SEE_SENSOR);
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.ROOT, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertTrue(allowSeeEntity.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAllowingDeployApplication() {
        EntitlementManager allowSeeEntity = Entitlements.FineGrainedEntitlements.allowing(Entitlements.DEPLOY_APPLICATION);
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.ROOT, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertTrue(allowSeeEntity.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAllowingSeeAllServerInfo() {
        EntitlementManager allowSeeEntity = Entitlements.FineGrainedEntitlements.allowing(Entitlements.SEE_ALL_SERVER_INFO);
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.ROOT, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(allowSeeEntity.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertTrue(allowSeeEntity.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }

    // nonSecret
    public void testSeeNonSecretSensors() {
        EntitlementManager seeNonSecretSensors = Entitlements.FineGrainedEntitlements.seeNonSecretSensors();
        assertFalse(seeNonSecretSensors.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "password")));
        assertTrue(seeNonSecretSensors.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "any-sensor")));
    }

    // allOf
    public void testAllOfWithSeeEntityAndSeeSensors() {
        EntitlementManager allOf = Entitlements.FineGrainedEntitlements.allOf(
                Entitlements.FineGrainedEntitlements.allowing(Entitlements.SEE_ENTITY),
                Entitlements.FineGrainedEntitlements.allowing(Entitlements.SEE_SENSOR));
        assertFalse(allOf.isEntitled(null, Entitlements.ROOT, null));
        assertTrue(allOf.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(allOf.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertTrue(allOf.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(allOf.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(allOf.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }

    // anyOf
    public void testAnyOfWithSeeEntityAndSeeSensors() {
        EntitlementManager anyOf = Entitlements.FineGrainedEntitlements.anyOf(
                Entitlements.FineGrainedEntitlements.allowing(Entitlements.SEE_ENTITY),
                Entitlements.FineGrainedEntitlements.allowing(Entitlements.SEE_SENSOR));
        assertFalse(anyOf.isEntitled(null, Entitlements.ROOT, null));
        assertTrue(anyOf.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(anyOf.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertTrue(anyOf.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(anyOf.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(anyOf.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }

    // root
    public void testGlobalRootEntitlement() {
        EntitlementManager root = Entitlements.root();
        assertTrue(root.isEntitled(null, Entitlements.ROOT, null));
        assertTrue(root.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertTrue(root.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertTrue(root.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertTrue(root.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertTrue(root.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAppSpecificRootEntitlement() {
        EntitlementManager root = Entitlements.root();
        assertTrue(root.isEntitled(null, Entitlements.SEE_ENTITY, app));
        assertTrue(root.isEntitled(null, Entitlements.INVOKE_EFFECTOR, Entitlements.EntityAndItem.of(app, "any-eff")));
        assertTrue(root.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "any-sensor")));
        assertTrue(root.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "password")));
        assertTrue(root.isEntitled(null, Entitlements.DEPLOY_APPLICATION, Entitlements.EntityAndItem.of(app, null)));
        assertTrue(root.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }

    // minimal
    public void testGlobalMinimalEntitlement() {
        EntitlementManager minimal = Entitlements.minimal();
        assertFalse(minimal.isEntitled(null, Entitlements.ROOT, null));
        assertFalse(minimal.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(minimal.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertFalse(minimal.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(minimal.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(minimal.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAppSpecificMinimalEntitlement() {
        EntitlementManager minimal = Entitlements.minimal();
        assertFalse(minimal.isEntitled(null, Entitlements.SEE_ENTITY, app));
        assertFalse(minimal.isEntitled(null, Entitlements.INVOKE_EFFECTOR, Entitlements.EntityAndItem.of(app, "any-eff")));
        assertFalse(minimal.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "any-sensor")));
        assertFalse(minimal.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "password")));
        assertFalse(minimal.isEntitled(null, Entitlements.DEPLOY_APPLICATION, Entitlements.EntityAndItem.of(app, null)));
        assertFalse(minimal.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }

    // readOnly
    public void testGlobalReadOnlyEntitlement() {
        EntitlementManager readOnly = Entitlements.readOnly();
        assertFalse(readOnly.isEntitled(null, Entitlements.ROOT, null));
        assertTrue(readOnly.isEntitled(null, Entitlements.SEE_ENTITY, null));
        assertFalse(readOnly.isEntitled(null, Entitlements.INVOKE_EFFECTOR, null));
        assertFalse(readOnly.isEntitled(null, Entitlements.SEE_SENSOR, null));
        assertFalse(readOnly.isEntitled(null, Entitlements.DEPLOY_APPLICATION, null));
        assertFalse(readOnly.isEntitled(null, Entitlements.SEE_ALL_SERVER_INFO, null));
    }
    public void testAppSpecificReadOnlyEntitlement() {
        EntitlementManager readOnly = Entitlements.readOnly();
        assertTrue(readOnly.isEntitled(null, Entitlements.SEE_ENTITY, app));
        assertFalse(readOnly.isEntitled(null, Entitlements.INVOKE_EFFECTOR, Entitlements.EntityAndItem.of(app, "any-eff")));
        assertTrue(readOnly.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "any-sensor")));
        assertFalse(readOnly.isEntitled(null, Entitlements.SEE_SENSOR, Entitlements.EntityAndItem.of(app, "password")));
        assertFalse(readOnly.isEntitled(null, Entitlements.DEPLOY_APPLICATION, Entitlements.EntityAndItem.of(app, null)));
    }
}
