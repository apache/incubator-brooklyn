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
package brooklyn.entity.dns;

import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.group.DynamicRegionsFabric;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class AbstractGeoDnsServiceTest {
    public static final Logger log = LoggerFactory.getLogger(AbstractGeoDnsServiceTest.class);

    private static final String WEST_IP = "100.0.0.1";
    private static final String EAST_IP = "100.0.0.2";
    private static final double WEST_LATITUDE = 0, WEST_LONGITUDE = -60;
    private static final double EAST_LATITUDE = 0, EAST_LONGITUDE = 60;
    
    private static final String NORTH_IP = "10.0.0.1";
    private static final double NORTH_LATITUDE = 60, NORTH_LONGITUDE = 0;
    
    private ManagementContext managementContext;
    
    private Location westParent;
    private Location westChild;
    private Location westChildWithLocation; 
    private Location eastParent;
    private Location eastChild; 
    private Location eastChildWithLocationAndWithPrivateHostname; 

    private Location northParent;
    private Location northChildWithLocation; 

    private TestApplication app;
    private DynamicRegionsFabric fabric;
    private DynamicGroup testEntities;
    private GeoDnsTestService geoDns;
    

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        managementContext = new LocalManagementContext();
        
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
        ((BasicLocationRegistry)managementContext.getLocationRegistry()).registerResolver(new LocationResolver() {
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

        Locations.manage(westParent, managementContext);
        Locations.manage(eastParent, managementContext);
        Locations.manage(northParent, managementContext);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        fabric = app.createAndManageChild(EntitySpec.create(DynamicRegionsFabric.class)
            .configure(DynamicFabric.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));
        
        testEntities = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
            .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class)));
        geoDns = app.createAndManageChild(EntitySpec.create(GeoDnsTestService.class));
        geoDns.setTargetEntityProvider(testEntities);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    private SimulatedLocation newSimulatedLocation(String name, double lat, double lon) {
        return managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class)
                .displayName(name)
                .configure("latitude", lat)
                .configure("longitude", lon));
    }
    
    private Location newSshMachineLocation(String name, String address, Location parent) {
        return managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .parent(parent)
                .displayName(name)
                .configure("address", address));
    }
    
    private Location newSshMachineLocation(String name, String hostname, String address, Location parent, double lat, double lon) {
        return managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
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
        
        EntityTestUtils.assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        assertTrue(geoDns.getTargetHostsByName().containsKey("West child with location"), "targets="+geoDns.getTargetHostsByName());
        assertTrue(geoDns.getTargetHostsByName().containsKey("East child with location"), "targets="+geoDns.getTargetHostsByName());
    }
    
    @Test
    public void testGeoInfoOnParentLocation() {
        app.start( ImmutableList.of(westChild, eastChild) );
        publishSensors(2, true, false, false);
        
        EntityTestUtils.assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        assertTrue(geoDns.getTargetHostsByName().containsKey("West child"), "targets="+geoDns.getTargetHostsByName());
        assertTrue(geoDns.getTargetHostsByName().containsKey("East child"), "targets="+geoDns.getTargetHostsByName());
    }

    @Test
    public void testSubscribesToHostname() {
        ((EntityInternal)geoDns).setConfig(GeoDnsTestServiceImpl.ADD_ANYTHING, false);
        app.start( ImmutableList.of(westChild, eastChildWithLocationAndWithPrivateHostname) );
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 0);
        publishSensors(2, true, true, true);
        
        EntityTestUtils.assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 2);
        assertTrue(geoDns.getTargetHostsByName().containsKey("West child"), "targets="+geoDns.getTargetHostsByName());
        assertTrue(geoDns.getTargetHostsByName().containsKey("East child with location"), "targets="+geoDns.getTargetHostsByName());
    }

    protected void publishSensors(int expectedSize, boolean includeServiceUp, boolean includeHostname, boolean includeAddress) {
        // First wait for the right size of group; the dynamic group gets notified asynchronously
        // of nodes added/removed, so if we don't wait then might not set value for all members.
        EntityTestUtils.assertGroupSizeEqualsEventually(testEntities, expectedSize);
        
        for (Entity e: testEntities.getMembers()) {
            if (includeServiceUp)
                ((EntityInternal)e).setAttribute(Attributes.SERVICE_UP, true);
            
            SshMachineLocation l = Machines.findUniqueSshMachineLocation(e.getLocations()).get();
            if (includeAddress)
                ((EntityInternal)e).setAttribute(Attributes.ADDRESS, l.getAddress().getHostAddress());
            String h = (String) l.getAllConfigBag().getStringKey("hostname");
            if (h==null) h = l.getAddress().getHostName();
            if (includeHostname)
                ((EntityInternal)e).setAttribute(Attributes.HOSTNAME, h);
        }
    }
    
    @Test
    public void testChildAddedLate() {
        app.start( ImmutableList.of(westChild, eastChildWithLocationAndWithPrivateHostname) );
        publishSensors(2, true, false, false);
        EntityTestUtils.assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        
        String id3 = fabric.addRegion("test:north");
        publishSensors(3, true, false, false);
        try {
            EntityTestUtils.assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(3));
        } catch (Throwable e) {
            log.warn("Did not pick up third entity, targets are "+geoDns.getAttribute(AbstractGeoDnsService.TARGETS)+" (rethrowing): "+e);
            Exceptions.propagate(e);
        }
        assertTrue(geoDns.getTargetHostsByName().containsKey("North child"), "targets="+geoDns.getTargetHostsByName());
        
        log.info("targets: "+geoDns.getTargetHostsByName());
    }    


    @Test
    public void testFiltersEntirelyPrivate() {
        ((EntityInternal)geoDns).setConfig(GeoDnsTestServiceImpl.ADD_ANYTHING, false);
        app.start( ImmutableList.of(westChild, eastChildWithLocationAndWithPrivateHostname, northChildWithLocation) );
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 0);
        publishSensors(3, true, true, true);
        
        EntityTestUtils.assertAttributeEventually(geoDns, AbstractGeoDnsService.TARGETS, CollectionFunctionals.<String>mapSizeEquals(2));
        Assert.assertEquals(geoDns.getTargetHostsByName().size(), 2);
        assertTrue(geoDns.getTargetHostsByName().containsKey("West child"), "targets="+geoDns.getTargetHostsByName());
        assertTrue(geoDns.getTargetHostsByName().containsKey("East child with location"), "targets="+geoDns.getTargetHostsByName());
        assertTrue(!geoDns.getTargetHostsByName().containsKey("North child"), "targets="+geoDns.getTargetHostsByName());
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
