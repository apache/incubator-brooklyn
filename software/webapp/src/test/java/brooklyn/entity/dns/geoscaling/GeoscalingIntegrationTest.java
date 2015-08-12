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
package brooklyn.entity.dns.geoscaling;

import static org.testng.Assert.assertEquals;

import java.net.InetAddress;

import org.apache.brooklyn.management.ManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.location.geo.HostGeoLookup;
import brooklyn.location.geo.MaxMind2HostGeoLookup;
import brooklyn.location.geo.UtraceHostGeoLookup;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.BrooklynSystemProperties;
import brooklyn.util.net.Networking;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

/**
 * {@link GeoscalingScriptGenerator} unit tests.
 */
public class GeoscalingIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(GeoscalingIntegrationTest.class);

    private final String primaryDomain = "geopaas.org";//"domain"+((int)(Math.random()*10000))+".test.org";
    private final String subDomain = "subdomain"+((int)(Math.random()*10000));
    private final InetAddress addrWithGeo = Networking.getLocalHost();
    private final InetAddress addrWithoutGeo = Networking.getInetAddressWithFixedName(StubHostGeoLookup.HOMELESS_IP);
    
    private ManagementContext mgmt;
    private TestApplication app;
    private TestEntity target;
    private DynamicGroup group;
    private GeoscalingDnsService geoDns;
    private String origGeoLookupImpl;

    private SshMachineLocation locWithGeo;
    private SshMachineLocation locWithoutGeo;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        origGeoLookupImpl = BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getValue();
        HostGeoInfo.clearCachedLookup();

        app = TestApplication.Factory.newManagedInstanceForTests();
        mgmt = app.getManagementContext();
        
        target = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        group = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class)));
        
        geoDns = app.createAndManageChild(EntitySpec.create(GeoscalingDnsService.class)
                .displayName("Geo-DNS")
                .configure("username", "cloudsoft")
                .configure("password", "cl0uds0ft")
                .configure("primaryDomainName", primaryDomain)
                .configure("smartSubdomainName", subDomain)
                .configure("targetEntityProvider", group));
        
        locWithGeo = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", addrWithGeo)
                .configure("name", "Edinburgh")
                .configure("latitude", 55.94944)
                .configure("longitude", -3.16028)
                .configure("iso3166", ImmutableList.of("GB-EDH")));

        locWithoutGeo = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", addrWithoutGeo)
                .configure("name", "Nowhere"));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (origGeoLookupImpl != null) {
            System.setProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName(), origGeoLookupImpl);
        } else {
            System.clearProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName());
        }
        if (mgmt != null) Entities.destroyAll(mgmt);
        HostGeoInfo.clearCachedLookup();
    }
    
    @Test(groups={"Integration"})
    public void testRoutesToExpectedLocation() {
        // Without this config, running on a home network (i.e. no public IP) the entity will have a private IP and will be ignored
        ((EntityLocal)geoDns).setConfig(GeoscalingDnsService.INCLUDE_HOMELESS_ENTITIES, true);
        
        target.setAttribute(Attributes.HOSTNAME,addrWithGeo.getHostName());
        
        app.start(ImmutableList.of(locWithGeo));
        
        LOG.info("geo-scaling test, using {}.{}; expect to be wired to {}", new Object[] {subDomain, primaryDomain, addrWithGeo});
        
        assertTargetHostsEventually(geoDns, 1);
    }
    
    @Test(groups={"Integration"})
    public void testIgnoresAddressWithoutGeography() throws Exception {
        System.setProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName(), StubHostGeoLookup.class.getName());
        ((EntityLocal)geoDns).setConfig(GeoscalingDnsService.INCLUDE_HOMELESS_ENTITIES, false); // false is default
        
        app.start(ImmutableList.of(locWithoutGeo));
        target.setAttribute(Attributes.HOSTNAME, StubHostGeoLookup.HOMELESS_IP);
        
        LOG.info("geo-scaling test, using {}.{}; expect not to be wired to {}", new Object[] {subDomain, primaryDomain, addrWithoutGeo});
        
        Asserts.succeedsContinually(MutableMap.of("timeout", 10*1000), new Runnable() {
            @Override public void run() {
                assertEquals(geoDns.getTargetHosts().size(), 0, "targets="+geoDns.getTargetHosts());
            }
        });
    }

    @Test(groups={"Integration"})
    public void testIncludesAddressWithoutGeography() {
        System.setProperty(BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName(), StubHostGeoLookup.class.getName());
        ((EntityLocal)geoDns).setConfig(GeoscalingDnsService.INCLUDE_HOMELESS_ENTITIES, true);
        
        app.start(ImmutableList.of(locWithoutGeo));
        target.setAttribute(Attributes.HOSTNAME, StubHostGeoLookup.HOMELESS_IP);
        
        LOG.info("geo-scaling test, using {}.{}; expect to be wired to {}", new Object[] {subDomain, primaryDomain, addrWithoutGeo});
        
        assertTargetHostsEventually(geoDns, 1);
    }

    private void assertTargetHostsEventually(final GeoscalingDnsService geoDns, final int numExpected) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(geoDns.getTargetHosts().size(), 1, "targets="+geoDns.getTargetHosts());
            }
        });
    }
    
    public static class StubHostGeoLookup implements HostGeoLookup {
        public static final String HOMELESS_IP = "1.2.3.4";
        private final HostGeoLookup delegate;

        public StubHostGeoLookup() throws Exception {
            this(null);
        }
        
        public StubHostGeoLookup(String delegateImpl) throws Exception {
            if (delegateImpl == null) {
                // don't just call HostGeoInfo.getDefaultLookup; this is the default lookup!
                if (MaxMind2HostGeoLookup.getDatabaseReader()!=null) {
                    delegate = new MaxMind2HostGeoLookup();
                } else {
                    delegate = new UtraceHostGeoLookup();
                }
            } else {
                delegate = (HostGeoLookup) Class.forName(delegateImpl).newInstance();
            }
        }

        @Override
        public HostGeoInfo getHostGeoInfo(InetAddress address) throws Exception {
            // Saw strange test failure on jenkins: hence paranoid logging, just in case exception is swallowed somehow.
            try {
                HostGeoInfo result;
                if (HOMELESS_IP.equals(address.getHostAddress())) {
                    result = null;
                } else {
                    result = delegate.getHostGeoInfo(address);
                }
                LOG.info("StubHostGeoLookup.getHostGeoInfo queried: address="+address+"; hostAddress="+address.getHostAddress()+"; result="+result);
                return result;
            } catch (Throwable t) {
                LOG.error("StubHostGeoLookup.getHostGeoInfo encountered problem (rethrowing): address="+address+"; hostAddress="+address.getHostAddress(), t);
                throw Exceptions.propagate(t);
            }
        }
    }
}
