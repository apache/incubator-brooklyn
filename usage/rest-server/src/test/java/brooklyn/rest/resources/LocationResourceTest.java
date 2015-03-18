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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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

import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.test.Asserts;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class LocationResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(LocationResourceTest.class);
    private URI addedLocationUri;
    
    @Test
    public void testAddNewLocation() {
        Map<String, String> expectedConfig = ImmutableMap.of(
                "identity", "bob",
                "credential", "CR3dential");
        ClientResponse response = client().resource("/v1/locations")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, new LocationSpec("my-jungle", "aws-ec2:us-east-1", expectedConfig));

        addedLocationUri = response.getLocation();
        log.info("added, at: " + addedLocationUri);
        LocationSummary location = client().resource(response.getLocation()).get(LocationSummary.class);
        log.info(" contents: " + location);
        Assert.assertEquals(location.getSpec(), "brooklyn.catalog:my-jungle:0.0.0.SNAPSHOT");
        Assert.assertTrue(addedLocationUri.toString().startsWith("/v1/locations/"));

        JcloudsLocation l = (JcloudsLocation) getManagementContext().getLocationRegistry().resolve("my-jungle");
        Assert.assertEquals(l.getProvider(), "aws-ec2");
        Assert.assertEquals(l.getRegion(), "us-east-1");
        Assert.assertEquals(l.getIdentity(), "bob");
        Assert.assertEquals(l.getCredential(), "CR3dential");
    }

    @Test(dependsOnMethods = { "testAddNewLocation" })
    public void testListAllLocations() {
        Set<LocationSummary> locations = client().resource("/v1/locations")
                .get(new GenericType<Set<LocationSummary>>() {});
        Iterable<LocationSummary> matching = Iterables.filter(locations, new Predicate<LocationSummary>() {
            @Override
            public boolean apply(@Nullable LocationSummary l) {
                return "my-jungle".equals(l.getName());
            }
        });
        LocationSummary location = Iterables.getOnlyElement(matching);
        assertThat(location.getSpec(), is("brooklyn.catalog:my-jungle:0.0.0.SNAPSHOT"));
        Assert.assertEquals(location.getLinks().get("self"), addedLocationUri);
    }

    @Test(dependsOnMethods = { "testListAllLocations" })
    public void testGetASpecificLocation() {
        LocationSummary location = client().resource(addedLocationUri.toString()).get(LocationSummary.class);
        assertThat(location.getSpec(), is("brooklyn.catalog:my-jungle:0.0.0.SNAPSHOT"));
    }

    @Test(dependsOnMethods = { "testGetASpecificLocation" })
    public void testDeleteLocation() {
        final int size = getLocationRegistry().getDefinedLocations().size();

        ClientResponse response = client().resource(addedLocationUri).delete(ClientResponse.class);
        assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertThat(getLocationRegistry().getDefinedLocations().size(), is(size - 1));
            }
        });
    }
}
