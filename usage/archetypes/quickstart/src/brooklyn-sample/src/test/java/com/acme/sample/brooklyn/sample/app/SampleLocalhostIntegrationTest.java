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
package com.acme.sample.brooklyn.sample.app;

import java.util.Arrays;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.text.Strings;

/**
 * Sample integration tests which show how to launch the sample applications on localhost,
 * make some assertions about them, and then destroy them.
 */
@Test(groups="Integration")
public class SampleLocalhostIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SampleLocalhostIntegrationTest.class);
    
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        mgmt = new LocalManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }


    public void testSingle() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, SingleWebServerSample.class));
        Entities.startManagement(app, mgmt);
        Entities.start(app, Arrays.asList(mgmt.getLocationRegistry().resolve("localhost")));
        
        Iterator<Entity> children = app.getChildren().iterator();
        if (!children.hasNext()) Assert.fail("Should have had a single JBoss child; had none");
        
        Entity web = children.next();
        
        if (children.hasNext()) Assert.fail("Should have had a single JBoss child; had too many: "+app.getChildren());
        
        String url = web.getAttribute(JavaWebAppService.ROOT_URL);
        Assert.assertNotNull(url);
        
        String page = new ResourceUtils(this).getResourceAsString(url);
        log.info("Read web page for "+app+" from "+url+":\n"+page);
        Assert.assertTrue(!Strings.isBlank(page));
    }

    public void testCluster() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, ClusterWebServerDatabaseSample.class));
        Entities.startManagement(app, mgmt);
        Entities.start(app, Arrays.asList(mgmt.getLocationRegistry().resolve("localhost")));
        
        log.debug("APP is started");
        
        String url = app.getAttribute(JavaWebAppService.ROOT_URL);
        Assert.assertNotNull(url);

        String page = new ResourceUtils(this).getResourceAsString(url);
        log.info("Read web page for "+app+" from "+url+":\n"+page);
        Assert.assertTrue(!Strings.isBlank(page));
    }
    
}
