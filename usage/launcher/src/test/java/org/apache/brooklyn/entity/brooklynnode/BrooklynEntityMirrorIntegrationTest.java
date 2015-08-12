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
package org.apache.brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.HttpTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.brooklynnode.BrooklynEntityMirror;
import brooklyn.entity.proxying.EntitySpec;

import org.apache.brooklyn.launcher.BrooklynWebServer;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.rest.filter.BrooklynPropertiesSecurityFilter;

import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

/**
 * Test for EntityMirror, launching an in-memory server and ensuring we can mirror.
 * Here so that we can access the REST server.
 * <p>
 * May require <code>-Dbrooklyn.localhost.address=127.0.0.1</code> so that the console which binds to localhost is addressible.
 * (That and the time it takes to run are the only reasons this is Integration.)
 */
@Test
public class BrooklynEntityMirrorIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynEntityMirrorIntegrationTest.class);
    
    private BrooklynWebServer server;
    private TestApplication serverApp;
    private ManagementContext serverMgmt;
    
    private TestApplication localApp;
    private ManagementContext localMgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        localApp = TestApplication.Factory.newManagedInstanceForTests();
        localMgmt = localApp.getManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (serverMgmt!=null) Entities.destroyAll(serverMgmt);
        if (server!=null) server.stop();
        if (localMgmt!=null) Entities.destroyAll(localMgmt);
        
        serverMgmt = null;
    }

    
    protected void setUpServer() {
        setUpServer(new LocalManagementContextForTests(), false);
    }
    protected void setUpServer(ManagementContext mgmt, boolean useSecurityFilter) {
        try {
            if (serverMgmt!=null) throw new IllegalStateException("server already set up");
            
            serverMgmt = mgmt;
            server = new BrooklynWebServer(mgmt);
            if (useSecurityFilter) server.setSecurityFilter(BrooklynPropertiesSecurityFilter.class);
            server.start();
            
            serverMgmt.getHighAvailabilityManager().disabled();
            serverApp = ApplicationBuilder.newManagedApp(TestApplication.class, serverMgmt);
            
            ((LocalManagementContextForTests)serverMgmt).noteStartupComplete();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    protected String getBaseUri() {
        return server.getRootUrl();
    }
    
    @Test(groups="Integration")
    public void testServiceMirroring() throws Exception {
        setUpServer();
        
        String catalogItemId = "test-catalog-item:1.0";
        String catalogItemIdGA = "test-catalog-item:1.0-GA";
        serverApp.setAttribute(TestApplication.MY_ATTRIBUTE, "austria");
        serverApp.setCatalogItemId(catalogItemId);

        String serviceId = serverApp.getId();
        Entity mirror = localApp.addChild(EntitySpec.create(BrooklynEntityMirror.class)
            .configure(BrooklynEntityMirror.POLL_PERIOD, Duration.millis(100))
            .configure(BrooklynEntityMirror.MIRRORED_ENTITY_ID, serviceId)
            .configure(BrooklynEntityMirror.MIRRORED_ENTITY_URL, 
                getBaseUri()+"/v1/applications/"+serviceId+"/entities/"+serviceId)
        );

        EntityTestUtils.assertAttributeEqualsEventually(mirror, TestApplication.MY_ATTRIBUTE, "austria");
        EntityTestUtils.assertAttributeEqualsEventually(mirror, BrooklynEntityMirror.MIRROR_CATALOG_ITEM_ID, catalogItemId);
        assertTrue(mirror.getAttribute(BrooklynEntityMirror.MIRROR_SUMMARY) != null, "entity summary is null");
        log.info("Sensors mirrored are: "+((EntityInternal)mirror).getAllAttributes());
        
        serverApp.setAttribute(TestApplication.MY_ATTRIBUTE, "bermuda");
        serverApp.setCatalogItemId(catalogItemIdGA);
        EntityTestUtils.assertAttributeEqualsEventually(mirror, TestApplication.MY_ATTRIBUTE, "bermuda");
        EntityTestUtils.assertAttributeEqualsEventually(mirror, BrooklynEntityMirror.MIRROR_CATALOG_ITEM_ID, catalogItemIdGA);

        serverApp.stop();
        assertUnmanagedEventually(mirror);
    }

    @Test(groups="Integration")
    public void testServiceMirroringHttps() throws Exception {
        LocalManagementContextForTests mgmtHttps = new LocalManagementContextForTests();
        mgmtHttps.getBrooklynProperties().put("brooklyn.webconsole.security.https.required", true);
        mgmtHttps.getBrooklynProperties().put("brooklyn.webconsole.security.users", "admin");
        mgmtHttps.getBrooklynProperties().put("brooklyn.webconsole.security.user.admin.password", "P5ssW0rd");

        setUpServer(mgmtHttps, true);
        Assert.assertTrue(getBaseUri().startsWith("https:"), "URL is not https: "+getBaseUri());
        // check auth is required
        HttpTestUtils.assertHttpStatusCodeEquals(getBaseUri(), 401);
        
        serverApp.setAttribute(TestApplication.MY_ATTRIBUTE, "austria");

        String serviceId = serverApp.getId();
        Entity mirror = localApp.addChild(EntitySpec.create(BrooklynEntityMirror.class)
            .configure(BrooklynEntityMirror.POLL_PERIOD, Duration.millis(100))
            .configure(BrooklynEntityMirror.MANAGEMENT_USER, "admin")
            .configure(BrooklynEntityMirror.MANAGEMENT_PASSWORD, "P5ssW0rd")
            .configure(BrooklynEntityMirror.MIRRORED_ENTITY_ID, serviceId)
            .configure(BrooklynEntityMirror.MIRRORED_ENTITY_URL, 
                getBaseUri()+"/v1/applications/"+serviceId+"/entities/"+serviceId)
        );

        EntityTestUtils.assertAttributeEqualsEventually(mirror, TestApplication.MY_ATTRIBUTE, "austria");
        log.info("Sensors mirrored are: "+((EntityInternal)mirror).getAllAttributes());
        
        serverApp.setAttribute(TestApplication.MY_ATTRIBUTE, "bermuda");
        EntityTestUtils.assertAttributeEqualsEventually(mirror, TestApplication.MY_ATTRIBUTE, "bermuda");

        serverApp.stop();
        assertUnmanagedEventually(mirror);
    }

    private static void assertUnmanagedEventually(final Entity entity) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertFalse(Entities.isManaged(entity));
            }});
    }
}
