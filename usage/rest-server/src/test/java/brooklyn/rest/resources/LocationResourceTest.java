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
package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.rest.domain.CatalogLocationSummary;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.test.Asserts;

@Test(singleThreaded = true)
public class LocationResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(LocationResourceTest.class);
    private String legacyLocationName = "my-jungle-legacy";
    private String legacyLocationVersion = "0.0.0.SNAPSHOT";
    
    private String locationName = "my-jungle";
    private String locationVersion = "0.1.2";
    
    @Test
    @Deprecated
    public void testAddLegacyLocationDefinition() {
        Map<String, String> expectedConfig = ImmutableMap.of(
                "identity", "bob",
                "credential", "CR3dential");
        ClientResponse response = client().resource("/v1/locations")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, new brooklyn.rest.domain.LocationSpec(legacyLocationName, "aws-ec2:us-east-1", expectedConfig));

        URI addedLegacyLocationUri = response.getLocation();
        log.info("added legacy, at: " + addedLegacyLocationUri);
        LocationSummary location = client().resource(response.getLocation()).get(LocationSummary.class);
        log.info(" contents: " + location);
        assertEquals(location.getSpec(), "brooklyn.catalog:"+legacyLocationName+":"+legacyLocationVersion);
        assertTrue(addedLegacyLocationUri.toString().startsWith("/v1/locations/"));

        JcloudsLocation l = (JcloudsLocation) getManagementContext().getLocationRegistry().resolve(legacyLocationName);
        Assert.assertEquals(l.getProvider(), "aws-ec2");
        Assert.assertEquals(l.getRegion(), "us-east-1");
        Assert.assertEquals(l.getIdentity(), "bob");
        Assert.assertEquals(l.getCredential(), "CR3dential");
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAddNewLocationDefinition() {
        String yaml = Joiner.on("\n").join(ImmutableList.of(
                "brooklyn.catalog:",
                "  symbolicName: "+locationName,
                "  version: " + locationVersion,
                "",
                "brooklyn.locations:",
                "- type: "+"aws-ec2:us-east-1",
                "  brooklyn.config:",
                "    identity: bob",
                "    credential: CR3dential"));

        
        ClientResponse response = client().resource("/v1/catalog")
                .post(ClientResponse.class, yaml);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        

        URI addedCatalogItemUri = response.getLocation();
        log.info("added, at: " + addedCatalogItemUri);
        
        // Ensure location definition exists
        CatalogLocationSummary locationItem = client().resource("/v1/catalog/locations/"+locationName + "/" + locationVersion)
                .get(CatalogLocationSummary.class);
        log.info(" item: " + locationItem);
        LocationSummary locationSummary = client().resource(URI.create("/v1/locations/"+locationName+"/")).get(LocationSummary.class);
        log.info(" summary: " + locationSummary);
        Assert.assertEquals(locationSummary.getSpec(), "brooklyn.catalog:"+locationName+":"+locationVersion);

        // Ensure location is usable - can instantiate, and has right config
        JcloudsLocation l = (JcloudsLocation) getManagementContext().getLocationRegistry().resolve(locationName);
        Assert.assertEquals(l.getProvider(), "aws-ec2");
        Assert.assertEquals(l.getRegion(), "us-east-1");
        Assert.assertEquals(l.getIdentity(), "bob");
        Assert.assertEquals(l.getCredential(), "CR3dential");
    }

    @SuppressWarnings("deprecation")
    @Test(dependsOnMethods = { "testAddNewLocationDefinition" })
    public void testListAllLocationDefinitions() {
        Set<LocationSummary> locations = client().resource("/v1/locations")
                .get(new GenericType<Set<LocationSummary>>() {});
        Iterable<LocationSummary> matching = Iterables.filter(locations, new Predicate<LocationSummary>() {
            @Override
            public boolean apply(@Nullable LocationSummary l) {
                return locationName.equals(l.getName());
            }
        });
        LocationSummary location = Iterables.getOnlyElement(matching);
        
        URI expectedLocationUri = URI.create("/v1/locations/"+locationName);
        Assert.assertEquals(location.getSpec(), "brooklyn.catalog:"+locationName+":"+locationVersion);
        Assert.assertEquals(location.getLinks().get("self"), expectedLocationUri);
    }

    @SuppressWarnings("deprecation")
    @Test(dependsOnMethods = { "testListAllLocationDefinitions" })
    public void testGetSpecificLocation() {
        URI expectedLocationUri = URI.create("/v1/locations/"+locationName);
        LocationSummary location = client().resource(expectedLocationUri).get(LocationSummary.class);
        assertEquals(location.getSpec(), "brooklyn.catalog:"+locationName+":"+locationVersion);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetLocationConfig() {
        SimulatedLocation parentLoc = (SimulatedLocation) getManagementContext().getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class)
                .configure("myParentKey", "myParentVal"));
        SimulatedLocation loc = (SimulatedLocation) getManagementContext().getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class)
                .parent(parentLoc)
                .configure("mykey", "myval")
                .configure("password", "mypassword"));
    
        // "full" means including-inherited, filtered to exclude secrets
        URI uriFull = URI.create("/v1/locations/"+loc.getId()+"?full=true");
        LocationSummary summaryFull = client().resource(uriFull).get(LocationSummary.class);
        assertEquals(summaryFull.getConfig(), ImmutableMap.of("mykey", "myval", "myParentKey", "myParentVal"), "conf="+summaryFull.getConfig());
        
        // Default is local-only, filtered to exclude secrets
        URI uriDefault = URI.create("/v1/locations/"+loc.getId());
        LocationSummary summaryDefault = client().resource(uriDefault).get(LocationSummary.class);
        assertEquals(summaryDefault.getConfig(), ImmutableMap.of("mykey", "myval"), "conf="+summaryDefault.getConfig());
    }

    @Test(dependsOnMethods = { "testAddLegacyLocationDefinition" })
    @Deprecated
    public void testDeleteLocation() {
        final int size = getLocationRegistry().getDefinedLocations().size();
        URI expectedLocationUri = URI.create("/v1/locations/"+legacyLocationName);

        ClientResponse response = client().resource(expectedLocationUri).delete(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(getLocationRegistry().getDefinedLocations().size(), size - 1);
            }
        });
    }
}
