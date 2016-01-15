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
package org.apache.brooklyn.camp.brooklyn;

import java.io.Reader;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.camp.spi.Assembly;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ReloadBrooklynPropertiesTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(ReloadBrooklynPropertiesTest.class);
    
    private ManagementContext brooklynMgmt;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() {
        brooklynMgmt = new BrooklynCampPlatformLauncherNoServer().launch().getBrooklynMgmt();
    }
    
    @AfterMethod(alwaysRun=true)
    public void teardown() {
        if (brooklynMgmt!=null) Entities.destroyAll(brooklynMgmt);
    }
    
    @Test
    public void testReloadBrooklynPropertiesNonDeploy() {
        CampPlatform platform = brooklynMgmt.getConfig().getConfig(BrooklynCampConstants.CAMP_PLATFORM);
        Assert.assertNotNull(platform);
        brooklynMgmt.reloadBrooklynProperties();
        CampPlatform reloadedPlatform = brooklynMgmt.getConfig().getConfig(BrooklynCampConstants.CAMP_PLATFORM);
        Assert.assertEquals(reloadedPlatform, platform);
    }
    
    @Test
    public void testReloadBrooklynPropertiesDeploy() {
        brooklynMgmt.reloadBrooklynProperties();
        CampPlatform reloadedPlatform = brooklynMgmt.getConfig().getConfig(BrooklynCampConstants.CAMP_PLATFORM);
        Assert.assertNotNull(reloadedPlatform);
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("test-entity-basic-template.yaml"));
        AssemblyTemplate template = reloadedPlatform.pdp().registerDeploymentPlan(input);
        try {
            Assembly assembly = template.getInstantiator().newInstance().instantiate(template, reloadedPlatform);
            LOG.info("Test - created " + assembly);
            final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            LOG.info("App - " + app);
            Assert.assertEquals(app.getDisplayName(), "test-entity-basic-template");
            EntityTestUtils.assertAttributeEqualsEventually(app, Startable.SERVICE_UP, true);
        } catch (Exception e) {
            LOG.warn("Unable to instantiate " + template + " (rethrowing): " + e);
            throw Exceptions.propagate(e);
        }
    }
}
