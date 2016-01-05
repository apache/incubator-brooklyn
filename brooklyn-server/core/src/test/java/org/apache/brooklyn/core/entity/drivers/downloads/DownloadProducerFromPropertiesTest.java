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
import org.apache.brooklyn.core.entity.drivers.downloads.DownloadProducerFromProperties;
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

public class DownloadProducerFromPropertiesTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Location loc;
    private TestApplication app;
    private TestEntity entity;
    private MyEntityDriver driver;
    private DownloadProducerFromProperties resolver;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(DownloadProducerFromLocalRepo.LOCAL_REPO_ENABLED, false);
        managementContext = new LocalManagementContext(brooklynProperties);
        
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
        
        resolver = new DownloadProducerFromProperties(brooklynProperties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testReturnsEmptyWhenEmpty() throws Exception {
        assertResolves(ImmutableList.<String>of(), ImmutableList.<String>of());
    }
    
    @Test
    public void testReturnsGlobalUrl() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "myurl");
        assertResolves("myurl");
    }
    
    @Test
    public void testReturnsGlobalUrlsSplitOnSemicolon() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "myurl; myurl2");
        assertResolves("myurl", "myurl2");
    }
    
    @Test
    public void testReturnsGlobalFallbackUrl() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.fallbackurl", "myurl");
        assertResolves(ImmutableList.<String>of(), ImmutableList.of("myurl"));
    }

    @Test
    public void testSubstitutionsAppliedToFallbackUrl() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.fallbackurl", "version=${version}");
        entity.config().set(BrooklynConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves(ImmutableList.<String>of(), ImmutableList.of("version=myversion"));
    }

    @Test
    public void testReturnsGlobalFallbackUrlAsLast() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "myurl");
        brooklynProperties.put("brooklyn.downloads.all.fallbackurl", "myurl2");
        assertResolves(ImmutableList.of("myurl"), ImmutableList.of("myurl2"));
    }
    
    @Test
    public void testReturnsGlobalUrlWithEntitySubstituions() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "version=${version}");
        entity.config().set(BrooklynConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("version=myversion");
    }
    
    @Test
    public void testEntitySpecificUrlOverridesGlobalUrl() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "version=${version}");
        brooklynProperties.put("brooklyn.downloads.entity.TestEntity.url", "overridden,version=${version}");
        entity.config().set(BrooklynConfigKeys.SUGGESTED_VERSION, "myversion");
        assertResolves("overridden,version=myversion", "version=myversion");
    }
    
    @Test
    public void testEntitySpecificAddonUsesGlobalUrl() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "version=${version}");

        DownloadRequirement req = new BasicDownloadRequirement(driver, "myaddon", ImmutableMap.of("version", "myversion"));
        assertResolves(req, ImmutableList.of("version=myversion"), ImmutableList.<String>of());
    }
    
    @Test
    public void testEntitySpecificAddonOverridesGlobalUrl() throws Exception {
        brooklynProperties.put("brooklyn.downloads.all.url", "${addon}-${version}");
        brooklynProperties.put("brooklyn.downloads.entity.TestEntity.addon.myaddon.url", "overridden,${addon}-${version}");

        DownloadRequirement req = new BasicDownloadRequirement(driver, "myaddon", ImmutableMap.of("version", "myversion"));
        assertResolves(req, ImmutableList.of("overridden,myaddon-myversion", "myaddon-myversion"), ImmutableList.<String>of());
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
        assertEquals(actual.getPrimaryLocations(), expectedPrimaries, "actual="+actual);
        assertEquals(actual.getFallbackLocations(), expectedFallbacks, "actual="+actual);
    }
}
