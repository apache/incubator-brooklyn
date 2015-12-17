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
package org.apache.brooklyn.entity.dns;

import static org.apache.brooklyn.core.entity.EntityAsserts.assertAttributeEqualsContinually;
import static org.apache.brooklyn.core.entity.EntityAsserts.assertAttributeEventually;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationResolver;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.DynamicFabric;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.group.DynamicRegionsFabric;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class AbstractGeoDnsServiceTest extends BrooklynAppUnitTestSupport {

    public static final Logger log = LoggerFactory.getLogger(AbstractGeoDnsServiceTest.class);

    private static final String WEST_IP = "100.0.0.1";
    private static final String EAST_IP = "100.0.0.2";
    private static final double WEST_LATITUDE = 0, WEST_LONGITUDE = -60;
    private static final double EAST_LATITUDE = 0, EAST_LONGITUDE = 60;
    
    private static final String NORTH_IP = "10.0.0.1";
    private static final double NORTH_LATITUDE = 60, NORTH_LONGITUDE = 0;
    
    private Location westParent;
    private Location westChild;
    private Location westChildWithLocation; 
    private Location eastParent;
    private Location eastChild; 
    private Location eastChildWithLocationAndWithPrivateHostname; 

    private Location northParent;
    private Location northChildWithLocation; 

    private DynamicRegionsFabric fabric;
    private DynamicGroup testEntities;
    private GeoDnsTestService geoDns;
    
    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        westParent = newSimulatedLocation("West parent", WEST_LATITUDE, WEST_LONGITUDE);
        
        // west uses public IP for name, so is always picked up
        westChild = newSshMachineLocation("West child", WEST_IP, westParent);
        westChildWithLocation = newSshMachineLocation("West child with location", WEST_IP, WEST_IP, westParent, WEST_LATITUDE, WEST_LONGITUDE); 
        
        // east has public IP but private IP hostname, so should also be picked up but by a different path
        eastParent = newSimulatedLocation("East parent", EAST_LATITUDE, EAST_LONGITUDE);
        eastChild = newSshMachineLocation("East child", EAST_IP, eastParent); 
        eastChildWithLocationAndWithPrivateHostname = newSshMachineLocation("East child with location", "localhost", EAST_IP, eastParent, EAST_LATITUDE, EAST_LONGITUDE); 

        // north has a private IP and private hostname so should not be picked up when we turn off ADD_ANYTHING
        northParent = newSimulatedLocation("North parent", NORTH_LATITUDE, NORTH_LONGITUDE);
        northChildWithLocation = newSshMachineLocation("North child", "localhost", NORTH_IP, northParent, NORTH_LATITUDE, NORTH_LONGITUDE);
        ((BasicLocationRegistry) mgmt.getLocationRegistry()).registerResolver(new LocationResolver() {
            @Override
            public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
                if (!spec.equals("test:north")) throw new IllegalStateException("unsupported");
                return northChildWithLocation;
            }
            @Override
            public void init(ManagementContext managementContext) {
            }
            @Override
            public String getPrefix() {
                return "test";
            }
            @Override
            public boolean accepts(String spec, LocationRegistry registry) {
                return spec.startsWith(getPrefix());
            }
        });

        Locations.manage(westParent, mgmt);
        Locations.manage(eastParent, mgmt);
        Locations.manage(northParent, mgmt);
        
        fabric = app.createAndManageChild(EntitySpec.create(DynamicRegionsFabric.class)
            .configure(DynamicFabric.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));

        testEntities = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
            .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class)));

        geoDns = app.createAndManageChild(EntitySpec.create(GeoDnsTestService.class)
            .configure(AbstractGeoDnsService.ENTITY_PROVIDER, testEntities));
    }

    private SimulatedLocation newSimulatedLocation(String name, double lat, double lon) {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class)
                .displayName(name)
                .configure("latitude", lat)
                .configure("longitude", lon));
    }
    
    private Location newSshMachineLocation(String name, String address, Location parent) {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .parent(parent)
                .displayName(name)
                .configure("address", address));
    }
    
    private Location newSshMachineLocation(String name, String hostname, String address, Location parent, double lat, double lon) {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .parent(parent)
                .displayName(name)
                .configure("hostname", hostname)
                .configure("address", address)
                .configure("latitude", lat)
                .configure("longitude", lon));
    }
    
    @Test
    public void testGeoInfoOnLocation() {
        app.start( ImmutableList.of(westChildWithLocation, eastChildWithLocationAndWithPrivateHostname) );
        publishSensors(2, true, true, true);
        
        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        assertIsTarget("West child with location");
        assertIsTarget("East child with location");
    }

    @Test
    public void testGeoInfoOnParentLocation() {
        app.start( ImmutableList.of(westChild, eastChild) );
        publishSensors(2, true, false, false);

        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        assertIsTarget("West child");
        assertIsTarget("East child");
    }

    @Test
    public void testSubscribesToHostname() {
        geoDns.config().set(GeoDnsTestServiceImpl.ADD_ANYTHING, false);
        app.start( ImmutableList.of(westChild, eastChildWithLocationAndWithPrivateHostname) );
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 0);
        publishSensors(2, true, true, true);

        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 2);
        assertIsTarget("West child");
        assertIsTarget("East child with location");
    }

    protected void publishSensors(int expectedSize, boolean includeServiceUp, boolean includeHostname, boolean includeAddress) {
        // First wait for the right size of group; the dynamic group gets notified asynchronously
        // of nodes added/removed, so if we don't wait then might not set value for all members.
        EntityAsserts.assertGroupSizeEqualsEventually(testEntities, expectedSize);

        for (Entity e: testEntities.getMembers()) {
            if (includeServiceUp)
                e.sensors().set(Attributes.SERVICE_UP, true);

            SshMachineLocation l = Machines.findUniqueMachineLocation(e.getLocations(), SshMachineLocation.class).get();
            if (includeAddress)
                e.sensors().set(Attributes.ADDRESS, l.getAddress().getHostAddress());
            String h = (String) l.config().getBag().getStringKey("hostname");
            if (h==null) h = l.getAddress().getHostName();
            if (includeHostname)
                e.sensors().set(Attributes.HOSTNAME, h);
        }
    }

    @Test
    public void testChildAddedLate() {
        app.start( ImmutableList.of(westChild, eastChildWithLocationAndWithPrivateHostname) );
        publishSensors(2, true, false, false);
        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));

        String id3 = fabric.addRegion("test:north");
        publishSensors(3, true, false, false);
        try {
            assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(3));
        } catch (Throwable e) {
            log.warn("Did not pick up third entity, targets are "+geoDns.getAttribute(AbstractGeoDnsService.TARGETS)+" (rethrowing): "+e);
            Exceptions.propagate(e);
        }
        assertIsTarget("North child");

        log.info("targets: "+geoDns.getTargetHostsByName());
    }

    @Test
    public void testFiltersEntirelyPrivate() {
        geoDns.config().set(GeoDnsTestServiceImpl.ADD_ANYTHING, false);
        app.start( ImmutableList.of(westChild, eastChildWithLocationAndWithPrivateHostname, northChildWithLocation) );
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 0);
        publishSensors(3, true, true, true);

        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 2);
        assertIsTarget("West child");
        assertIsTarget("East child with location");
        assertIsNotTarget("North child");
    }

    @Test
    public void testFiltersForRunningEntities() {
        app.start(ImmutableList.of(westChildWithLocation, eastChildWithLocationAndWithPrivateHostname));
        publishSensors(2, true, true, true);

        TestEntity problemChild = Entities.descendants(app, TestEntity.class).iterator().next();
        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        problemChild.sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(1));
        problemChild.sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
    }

    @Test
    public void testCanDisableFilterForRunningEntities() throws Exception {
        geoDns.config().set(AbstractGeoDnsService.FILTER_FOR_RUNNING, false);
        app.start(ImmutableList.of(westChildWithLocation, eastChildWithLocationAndWithPrivateHostname));
        publishSensors(2, true, true, true);

        assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        final Map<String, String> targets = ImmutableMap.copyOf(geoDns.sensors().get(AbstractGeoDnsService.TARGETS));
        TestEntity problemChild = Entities.descendants(app, TestEntity.class).iterator().next();
        problemChild.sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertAttributeEqualsContinually(geoDns, AbstractGeoDnsService.TARGETS, targets);
    }

    private void assertIsTarget(String target) {
        assertTrue(geoDns.getTargetHostsByName().containsKey(target), "targets=" + geoDns.getTargetHostsByName());
    }

    private void assertIsNotTarget(String target) {
        assertFalse(geoDns.getTargetHostsByName().containsKey(target), "targets=" + geoDns.getTargetHostsByName());
    }

    @ImplementedBy(GeoDnsTestServiceImpl.class)
    public static interface GeoDnsTestService extends AbstractGeoDnsService {
        public Map<String, HostGeoInfo> getTargetHostsByName();
    }
    
    public static class GeoDnsTestServiceImpl extends AbstractGeoDnsServiceImpl implements GeoDnsTestService {
        public Map<String, HostGeoInfo> targetHostsByName = new LinkedHashMap<String, HostGeoInfo>();

        public static final ConfigKey<Boolean> ADD_ANYTHING = ConfigKeys.newBooleanConfigKey("test.add.always", "", true);
        
        public GeoDnsTestServiceImpl() {
        }

        @Override
        public Map<String, HostGeoInfo> getTargetHostsByName() {
            synchronized (targetHostsByName) {
                return ImmutableMap.copyOf(targetHostsByName);
            }
        }
        
        @Override
        protected boolean addTargetHost(Entity e) {
            if (!getConfig(ADD_ANYTHING)) {
                return super.addTargetHost(e);
            } else {
                //ignore geo lookup, override parent menu
                if (e.getLocations().isEmpty()) {
                    log.info("GeoDns TestService ignoring target host {} (no location)", e);
                    return false;
                }
                Location l = Iterables.getOnlyElement(e.getLocations());
                HostGeoInfo geoInfo = new HostGeoInfo("<address-ignored>", l.getDisplayName(), 
                    l.getConfig(LocationConfigKeys.LATITUDE), l.getConfig(LocationConfigKeys.LONGITUDE));
                log.info("GeoDns TestService adding target host {} {}", e, geoInfo);
                targetHosts.put(e, geoInfo);
                return true;
            }
        }
        
        @Override
        protected void reconfigureService(Collection<HostGeoInfo> targetHosts) {
            synchronized (targetHostsByName) {
                targetHostsByName.clear();
                for (HostGeoInfo host : targetHosts) {
                    if (host != null) targetHostsByName.put(host.displayName, host);
                }
            }
        }

        @Override
        public String getHostname() {
            return "localhost";
        }
    }
    
}
