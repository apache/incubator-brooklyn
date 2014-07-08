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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

/** 
 * Unit tests for the sample applications defined in this project.
 * Shows how to examine the spec and make assertions about configuration.
 */
@Test
public class SampleUnitTest {

    private static final Logger log = LoggerFactory.getLogger(SampleUnitTest.class);

    
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        mgmt = new LocalManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    
    public void testSampleSingleStructure() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, SingleWebServerSample.class));
        log.info("app from spec is: "+app);
        
        Assert.assertEquals(app.getChildren().size(), 1);
        Assert.assertNotNull( app.getChildren().iterator().next().getConfig(JavaWebAppService.ROOT_WAR) );
    }
    
    public void testSampleClusterStructure() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, ClusterWebServerDatabaseSample.class));
        log.info("app from spec is: "+app);
        
        Assert.assertEquals(app.getChildren().size(), 2);
        
        Entity webappCluster = Iterables.find(app.getChildren(), Predicates.instanceOf(WebAppService.class));
        Entity database = Iterables.find(app.getChildren(), Predicates.instanceOf(DatastoreCommon.class));
        
        Assert.assertNotNull( webappCluster.getConfig(JavaWebAppService.ROOT_WAR) );
        Assert.assertNotNull( database.getConfig(MySqlNode.CREATION_SCRIPT_URL) );
    }

}
