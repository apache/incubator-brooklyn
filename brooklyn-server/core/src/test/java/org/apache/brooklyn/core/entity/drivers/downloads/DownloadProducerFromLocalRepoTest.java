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
package org.apache.brooklyn.core.entity.drivers.downloads;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import org.apache.brooklyn.api.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.drivers.downloads.BasicDownloadRequirement;
import org.apache.brooklyn.core.entity.drivers.downloads.DownloadProducerFromLocalRepo;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DownloadProducerFromLocalRepoTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private String entitySimpleType;
    private DownloadProducerFromLocalRepo resolver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        managementContext = new LocalManagementContext(brooklynProperties);
        
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
        entitySimpleType = TestEntity.class.getSimpleName();
        
        resolver = new DownloadProducerFromLocalRepo(brooklynProperties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testReturnsEmptyWhenDisabled() throws Exception {
        brooklynProperties.put(DownloadProducerFromLocalRepo.LOCAL_REPO_ENABLED, false);
        assertResolves(ImmutableList.<String>of(), ImmutableList.<String>of());
    }
    
    @Test
    public void testReturnsDefault() throws Exception {
        // uses default of ${simpletype}-${version}.tar.gz";
        String entityVersion = "myversion";
        String downloadFilename = (entitySimpleType+"-"+entityVersion+".tar.gz").toLowerCase();
        entity.config().set(BrooklynConfigKeys.SUGGESTED_VERSION, entityVersion);
        assertResolves(String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, downloadFilename));
    }
    
    @Test
    public void testReturnsFilenameFromDriver() throws Exception {
        String entityVersion = "myversion";
        String filename = "my.file.name";
        entity.config().set(BrooklynConfigKeys.SUGGESTED_VERSION, entityVersion);
        
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver, ImmutableMap.of("filename", filename));
        assertResolves(req, String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, filename));
    }
    
    @Test
    public void testReturnsFileSuffixFromRequirements() throws Exception {
        // uses ${driver.downloadFileSuffix}
        String entityVersion = "myversion";
        String fileSuffix = "mysuffix";
        String expectedFilename = (entitySimpleType+"-"+entityVersion+"."+fileSuffix).toLowerCase();
        entity.config().set(BrooklynConfigKeys.SUGGESTED_VERSION, entityVersion);
        
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver, ImmutableMap.of("fileSuffix", fileSuffix));
        assertResolves(req, String.format("file://$HOME/.brooklyn/repository/%s/%s/%s", entitySimpleType, entityVersion, expectedFilename));
    }
    
    private void assertResolves(String... expected) {
        assertResolves(ImmutableList.copyOf(expected), ImmutableList.<String>of());
    }
    
    private void assertResolves(List<String> expectedPrimaries, List<String> expectedFallbacks) {
        assertResolves(new BasicDownloadRequirement(driver), expectedPrimaries, expectedFallbacks);
    }
    
    private void assertResolves(DownloadRequirement req, String... expected) {
        assertResolves(req, ImmutableList.copyOf(expected), ImmutableList.<String>of());
    }

    private void assertResolves(DownloadRequirement req, List<String> expectedPrimaries, List<String> expectedFallbacks) {
        DownloadTargets actual = resolver.apply(req);
        assertEquals(actual.getPrimaryLocations(), expectedPrimaries);
        assertEquals(actual.getFallbackLocations(), expectedFallbacks);
    }
}
