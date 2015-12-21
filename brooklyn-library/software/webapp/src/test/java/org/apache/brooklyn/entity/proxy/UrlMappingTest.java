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
package org.apache.brooklyn.entity.proxy;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.HashSet;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.factory.BasicConfigurableEntityFactory;
import org.apache.brooklyn.core.entity.factory.EntityFactory;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.proxy.nginx.UrlMapping;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class UrlMappingTest {
    
    private static final Logger log = LoggerFactory.getLogger(UrlMappingTest.class);
    
    private final int initialClusterSize = 2;
    
    private ClassLoader classLoader = getClass().getClassLoader();
    private LocalManagementContext managementContext;
    private File mementoDir;
    
    private TestApplication app;
    private DynamicCluster cluster;
    private UrlMapping urlMapping;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);

        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        
        EntityFactory<StubAppServer> serverFactory = new BasicConfigurableEntityFactory<StubAppServer>(StubAppServer.class);
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", initialClusterSize)
                .configure("factory", serverFactory));

        urlMapping = app.createAndManageChild(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost")
                .configure("target", cluster));

        app.start( ImmutableList.of(
                managementContext.getLocationManager().createLocation(
                        LocationSpec.create(LocalhostMachineProvisioningLocation.class))
                ));
        log.info("app's location managed: "+managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(app.getLocations())));
        log.info("clusters's location managed: "+managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())));
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @Test(groups = "Integration")
    public void testTargetMappingsMatchesClusterMembers() {
        // Check updates its TARGET_ADDRESSES (through async subscription)
        assertExpectedTargetsEventually(cluster.getMembers());
    }
    
    @Test(groups = "Integration")
    public void testTargetMappingsRemovesUnmanagedMember() {
        Iterable<StubAppServer> members = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        assertEquals(Iterables.size(members), 2);
        StubAppServer target1 = Iterables.get(members, 0);
        StubAppServer target2 = Iterables.get(members, 1);
        
        // First wait for targets to be listed
        assertExpectedTargetsEventually(members);
        
        // Unmanage one member, and expect the URL Mapping to be updated accordingly
        Entities.unmanage(target1);

        assertExpectedTargetsEventually(ImmutableSet.of(target2));
    }
    
    @Test(groups = "Integration", invocationCount=50)
    public void testTargetMappingsRemovesUnmanagedMemberManyTimes() {
        testTargetMappingsRemovesUnmanagedMember();
    }
    
    @Test(groups = "Integration")
    public void testTargetMappingsRemovesDownMember() {
        Iterable<StubAppServer> members = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        StubAppServer target1 = Iterables.get(members, 0);
        StubAppServer target2 = Iterables.get(members, 1);
        
        // First wait for targets to be listed
        assertExpectedTargetsEventually(members);
        
        // Stop one member, and expect the URL Mapping to be updated accordingly
        target1.sensors().set(StubAppServer.SERVICE_UP, false);

        assertExpectedTargetsEventually(ImmutableSet.of(target2));
    }

    // i think no real reason for other methods to be Integration apart from the time they take;
    // having one in the unit tests is very handy however, and this is a good choice because it does quite a lot!
    @Test
    public void testTargetMappingUpdatesAfterRebind() throws Exception {
        log.info("starting testTargetMappingUpdatesAfterRebind");
        Iterable<StubAppServer> members = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        assertExpectedTargetsEventually(members);
        
        Assert.assertTrue(managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())));
        rebind();
        Assert.assertTrue(managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())),
                "location not managed after rebind");
        
        Iterable<StubAppServer> members2 = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        StubAppServer target1 = Iterables.get(members2, 0);
        StubAppServer target2 = Iterables.get(members2, 1);

        // Expect to have existing targets
        assertExpectedTargetsEventually(ImmutableSet.of(target1, target2));

        // Add a new member; expect member to be added
        log.info("resizing "+cluster+" - "+cluster.getChildren());
        Integer result = cluster.resize(3);
        Assert.assertTrue(managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())));
        log.info("resized "+cluster+" ("+result+") - "+cluster.getChildren());
        HashSet<StubAppServer> newEntities = Sets.newHashSet(Iterables.filter(cluster.getChildren(), StubAppServer.class));
        newEntities.remove(target1);
        newEntities.remove(target2);
        StubAppServer target3 = Iterables.getOnlyElement(newEntities);
        log.info("expecting "+ImmutableSet.of(target1, target2, target3));
        assertExpectedTargetsEventually(ImmutableSet.of(target1, target2, target3));
        
        // Stop one member, and expect the URL Mapping to be updated accordingly
        log.info("pretending one node down");
        target1.sensors().set(StubAppServer.SERVICE_UP, false);
        assertExpectedTargetsEventually(ImmutableSet.of(target2, target3));

        // Unmanage a member, and expect the URL Mapping to be updated accordingly
        log.info("unmanaging another node");
        Entities.unmanage(target2);
        assertExpectedTargetsEventually(ImmutableSet.of(target3));
        log.info("success - testTargetMappingUpdatesAfterRebind");
    }
    
    private void assertExpectedTargetsEventually(final Iterable<? extends Entity> members) {
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.ONE_MINUTE), new Runnable() {
            public void run() {
                Iterable<String> expectedTargets = Iterables.transform(members, new Function<Entity,String>() {
                        @Override public String apply(@Nullable Entity input) {
                            return input.getAttribute(Attributes.HOSTNAME)+":"+input.getAttribute(Attributes.HTTP_PORT);
                        }});

                assertEquals(ImmutableSet.copyOf(urlMapping.getAttribute(UrlMapping.TARGET_ADDRESSES)), ImmutableSet.copyOf(expectedTargets));
                assertEquals(urlMapping.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), Iterables.size(members));
            }});
    }
    
    private void rebind() throws Exception {
        RebindTestUtils.waitForPersisted(app);
        
        // Stop the old management context, so original nginx won't interfere
        managementContext.terminate();
        
        app = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        managementContext = (LocalManagementContext) ((EntityInternal)app).getManagementContext();
        cluster = (DynamicCluster) Iterables.find(app.getChildren(), Predicates.instanceOf(DynamicCluster.class));
        urlMapping = (UrlMapping) Iterables.find(app.getChildren(), Predicates.instanceOf(UrlMapping.class));
    }
}
