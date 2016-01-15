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
package org.apache.brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.osgi.OsgiStandaloneTest;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.rest.domain.CatalogEntitySummary;
import org.apache.brooklyn.rest.domain.CatalogItemSummary;
import org.apache.brooklyn.rest.domain.CatalogLocationSummary;
import org.apache.brooklyn.rest.domain.CatalogPolicySummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.reporters.Files;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

public class CatalogResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogResourceTest.class);
    
    private static String TEST_VERSION = "0.1.2";

    @BeforeClass(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        useLocalScannedCatalog();
        super.setUp();
    }
    
    @Override
    protected void addBrooklynResources() {
        addResource(new CatalogResource());
    }

    @Test
    /** based on CampYamlLiteTest */
    public void testRegisterCustomEntityTopLevelSyntaxWithBundleWhereEntityIsFromCoreAndIconFromBundle() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String symbolicName = "my.catalog.entity.id";
        String bundleUrl = OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL;
        String yaml =
                "brooklyn.catalog:\n"+
                "  id: " + symbolicName + "\n"+
                "  name: My Catalog App\n"+
                "  description: My description\n"+
                "  icon_url: classpath:/org/apache/brooklyn/test/osgi/entities/icon.gif\n"+
                "  version: " + TEST_VERSION + "\n"+
                "  libraries:\n"+
                "  - url: " + bundleUrl + "\n"+
                "\n"+
                "services:\n"+
                "- type: org.apache.brooklyn.core.test.entity.TestEntity\n";

        ClientResponse response = client().resource("/v1/catalog")
                .post(ClientResponse.class, yaml);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

        CatalogEntitySummary entityItem = client().resource("/v1/catalog/entities/"+symbolicName + "/" + TEST_VERSION)
                .get(CatalogEntitySummary.class);

        Assert.assertNotNull(entityItem.getPlanYaml());
        Assert.assertTrue(entityItem.getPlanYaml().contains("org.apache.brooklyn.core.test.entity.TestEntity"));

        assertEquals(entityItem.getId(), ver(symbolicName));
        assertEquals(entityItem.getSymbolicName(), symbolicName);
        assertEquals(entityItem.getVersion(), TEST_VERSION);

        // and internally let's check we have libraries
        RegisteredType item = getManagementContext().getTypeRegistry().get(symbolicName, TEST_VERSION);
        Assert.assertNotNull(item);
        Collection<OsgiBundleWithUrl> libs = item.getLibraries();
        assertEquals(libs.size(), 1);
        assertEquals(Iterables.getOnlyElement(libs).getUrl(), bundleUrl);

        // now let's check other things on the item
        assertEquals(entityItem.getName(), "My Catalog App");
        assertEquals(entityItem.getDescription(), "My description");
        assertEquals(entityItem.getIconUrl(), "/v1/catalog/icon/" + symbolicName + "/" + entityItem.getVersion());
        assertEquals(item.getIconUrl(), "classpath:/org/apache/brooklyn/test/osgi/entities/icon.gif");

        // an InterfacesTag should be created for every catalog item
        assertEquals(entityItem.getTags().size(), 1);
        Object tag = entityItem.getTags().iterator().next();
        List<String> actualInterfaces = ((Map<String, List<String>>) tag).get("traits");
        List<Class<?>> expectedInterfaces = Reflections.getAllInterfaces(TestEntity.class);
        assertEquals(actualInterfaces.size(), expectedInterfaces.size());
        for (Class<?> expectedInterface : expectedInterfaces) {
            assertTrue(actualInterfaces.contains(expectedInterface.getName()));
        }

        byte[] iconData = client().resource("/v1/catalog/icon/" + symbolicName + "/" + TEST_VERSION).get(byte[].class);
        assertEquals(iconData.length, 43);
    }

    @Test
    public void testRegisterOsgiPolicyTopLevelSyntax() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String symbolicName = "my.catalog.policy.id";
        String policyType = "org.apache.brooklyn.test.osgi.entities.SimplePolicy";
        String bundleUrl = OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL;

        String yaml =
                "brooklyn.catalog:\n"+
                "  id: " + symbolicName + "\n"+
                "  name: My Catalog App\n"+
                "  description: My description\n"+
                "  version: " + TEST_VERSION + "\n" +
                "  libraries:\n"+
                "  - url: " + bundleUrl + "\n"+
                "\n"+
                "brooklyn.policies:\n"+
                "- type: " + policyType;

        CatalogPolicySummary entityItem = Iterables.getOnlyElement( client().resource("/v1/catalog")
                .post(new GenericType<Map<String,CatalogPolicySummary>>() {}, yaml).values() );

        Assert.assertNotNull(entityItem.getPlanYaml());
        Assert.assertTrue(entityItem.getPlanYaml().contains(policyType));
        assertEquals(entityItem.getId(), ver(symbolicName));
        assertEquals(entityItem.getSymbolicName(), symbolicName);
        assertEquals(entityItem.getVersion(), TEST_VERSION);
    }

    @Test
    public void testListAllEntities() {
        List<CatalogEntitySummary> entities = client().resource("/v1/catalog/entities")
                .get(new GenericType<List<CatalogEntitySummary>>() {});
        assertTrue(entities.size() > 0);
    }

    @Test
    public void testListAllEntitiesAsItem() {
        // ensure things are happily downcasted and unknown properties ignored (e.g. sensors, effectors)
        List<CatalogItemSummary> entities = client().resource("/v1/catalog/entities")
                .get(new GenericType<List<CatalogItemSummary>>() {});
        assertTrue(entities.size() > 0);
    }

    @Test
    public void testFilterListOfEntitiesByName() {
        List<CatalogEntitySummary> entities = client().resource("/v1/catalog/entities")
                .queryParam("fragment", "brOOkLynENTITYmiRrOr").get(new GenericType<List<CatalogEntitySummary>>() {});
        assertEquals(entities.size(), 1);

        log.info("BrooklynEntityMirror-like entities are: " + entities);

        List<CatalogEntitySummary> entities2 = client().resource("/v1/catalog/entities")
                .queryParam("regex", "[Bb]ro+klynEntityMi[ro]+").get(new GenericType<List<CatalogEntitySummary>>() {});
        assertEquals(entities2.size(), 1);

        assertEquals(entities, entities2);
    
        List<CatalogEntitySummary> entities3 = client().resource("/v1/catalog/entities")
                .queryParam("fragment", "bweqQzZ").get(new GenericType<List<CatalogEntitySummary>>() {});
        assertEquals(entities3.size(), 0);

        List<CatalogEntitySummary> entities4 = client().resource("/v1/catalog/entities")
                .queryParam("regex", "bweq+z+").get(new GenericType<List<CatalogEntitySummary>>() {});
        assertEquals(entities4.size(), 0);
    }

    @Test
    @Deprecated
    // If we move to using a yaml catalog item, the details will be of the wrapping app,
    // not of the entity itself, so the test won't make sense any more.
    public void testGetCatalogEntityDetails() {
        CatalogEntitySummary details = client()
                .resource(URI.create("/v1/catalog/entities/org.apache.brooklyn.entity.brooklynnode.BrooklynNode"))
                .get(CatalogEntitySummary.class);
        assertTrue(details.toString().contains("download.url"), "expected more config, only got: "+details);
    }

    @Test
    @Deprecated
    // If we move to using a yaml catalog item, the details will be of the wrapping app,
    // not of the entity itself, so the test won't make sense any more.
    public void testGetCatalogEntityPlusVersionDetails() {
        CatalogEntitySummary details = client()
                .resource(URI.create("/v1/catalog/entities/org.apache.brooklyn.entity.brooklynnode.BrooklynNode:0.0.0.SNAPSHOT"))
                .get(CatalogEntitySummary.class);
        assertTrue(details.toString().contains("download.url"), "expected more config, only got: "+details);
    }

    @Test
    public void testGetCatalogEntityIconDetails() throws IOException {
        String catalogItemId = "testGetCatalogEntityIconDetails";
        addTestCatalogItemBrooklynNodeAsEntity(catalogItemId);
        ClientResponse response = client().resource(URI.create("/v1/catalog/icon/" + catalogItemId + "/" + TEST_VERSION))
                .get(ClientResponse.class);
        response.bufferEntity();
        Assert.assertEquals(response.getStatus(), 200);
        Assert.assertEquals(response.getType(), MediaType.valueOf("image/jpeg"));
        Image image = Toolkit.getDefaultToolkit().createImage(Files.readFile(response.getEntityInputStream()));
        Assert.assertNotNull(image);
    }

    private void addTestCatalogItemBrooklynNodeAsEntity(String catalogItemId) {
        addTestCatalogItem(catalogItemId, null, TEST_VERSION, "org.apache.brooklyn.entity.brooklynnode.BrooklynNode");
    }

    private void addTestCatalogItem(String catalogItemId, String itemType, String version, String service) {
        String yaml =
                "brooklyn.catalog:\n"+
                "  id: " + catalogItemId + "\n"+
                "  name: My Catalog App\n"+
                (itemType!=null ? "  item_type: "+itemType+"\n" : "")+
                "  description: My description\n"+
                "  icon_url: classpath:///brooklyn-test-logo.jpg\n"+
                "  version: " + version + "\n"+
                "\n"+
                "services:\n"+
                "- type: " + service + "\n";

        client().resource("/v1/catalog").post(yaml);
    }

    private enum DeprecateStyle {
        NEW_STYLE,
        LEGACY_STYLE
    }
    private void deprecateCatalogItem(DeprecateStyle style, String symbolicName, String version, boolean deprecated) {
        String id = String.format("%s:%s", symbolicName, version);
        ClientResponse response;
        if (style == DeprecateStyle.NEW_STYLE) {
            response = client().resource(String.format("/v1/catalog/entities/%s/deprecated", id))
                    .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON)
                    .post(ClientResponse.class, deprecated);
        } else {
            response = client().resource(String.format("/v1/catalog/entities/%s/deprecated/%s", id, deprecated))
                    .post(ClientResponse.class);
        }
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
    }

    private void disableCatalogItem(String symbolicName, String version, boolean disabled) {
        String id = String.format("%s:%s", symbolicName, version);
        ClientResponse getDisableResponse = client().resource(String.format("/v1/catalog/entities/%s/disabled", id))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON)
                .post(ClientResponse.class, disabled);
        assertEquals(getDisableResponse.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
    }

    @Test
    public void testListPolicies() {
        Set<CatalogPolicySummary> policies = client().resource("/v1/catalog/policies")
                .get(new GenericType<Set<CatalogPolicySummary>>() {});

        assertTrue(policies.size() > 0);
        CatalogItemSummary asp = null;
        for (CatalogItemSummary p : policies) {
            if (AutoScalerPolicy.class.getName().equals(p.getType()))
                asp = p;
        }
        Assert.assertNotNull(asp, "didn't find AutoScalerPolicy");
    }

    @Test
    public void testLocationAddGetAndRemove() {
        String symbolicName = "my.catalog.location.id";
        String locationType = "localhost";
        String yaml = Joiner.on("\n").join(
                "brooklyn.catalog:",
                "  id: " + symbolicName,
                "  name: My Catalog Location",
                "  description: My description",
                "  version: " + TEST_VERSION,
                "",
                "brooklyn.locations:",
                "- type: " + locationType);

        // Create location item
        Map<String, CatalogLocationSummary> items = client().resource("/v1/catalog")
                .post(new GenericType<Map<String,CatalogLocationSummary>>() {}, yaml);
        CatalogLocationSummary locationItem = Iterables.getOnlyElement(items.values());

        Assert.assertNotNull(locationItem.getPlanYaml());
        Assert.assertTrue(locationItem.getPlanYaml().contains(locationType));
        assertEquals(locationItem.getId(), ver(symbolicName));
        assertEquals(locationItem.getSymbolicName(), symbolicName);
        assertEquals(locationItem.getVersion(), TEST_VERSION);

        // Retrieve location item
        CatalogLocationSummary location = client().resource("/v1/catalog/locations/"+symbolicName+"/"+TEST_VERSION)
                .get(CatalogLocationSummary.class);
        assertEquals(location.getSymbolicName(), symbolicName);

        // Retrieve all locations
        Set<CatalogLocationSummary> locations = client().resource("/v1/catalog/locations")
                .get(new GenericType<Set<CatalogLocationSummary>>() {});
        boolean found = false;
        for (CatalogLocationSummary contender : locations) {
            if (contender.getSymbolicName().equals(symbolicName)) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found, "contenders="+locations);
        
        // Delete
        ClientResponse deleteResponse = client().resource("/v1/catalog/locations/"+symbolicName+"/"+TEST_VERSION)
                .delete(ClientResponse.class);
        assertEquals(deleteResponse.getStatus(), Response.Status.NO_CONTENT.getStatusCode());

        ClientResponse getPostDeleteResponse = client().resource("/v1/catalog/locations/"+symbolicName+"/"+TEST_VERSION)
                .get(ClientResponse.class);
        assertEquals(getPostDeleteResponse.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testDeleteCustomEntityFromCatalog() {
        String symbolicName = "my.catalog.app.id.to.subsequently.delete";
        String yaml =
                "name: "+symbolicName+"\n"+
                // FIXME name above should be unnecessary when brooklyn.catalog below is working
                "brooklyn.catalog:\n"+
                "  id: " + symbolicName + "\n"+
                "  name: My Catalog App To Be Deleted\n"+
                "  description: My description\n"+
                "  version: " + TEST_VERSION + "\n"+
                "\n"+
                "services:\n"+
                "- type: org.apache.brooklyn.core.test.entity.TestEntity\n";

        client().resource("/v1/catalog")
                .post(ClientResponse.class, yaml);

        ClientResponse deleteResponse = client().resource("/v1/catalog/entities/"+symbolicName+"/"+TEST_VERSION)
                .delete(ClientResponse.class);

        assertEquals(deleteResponse.getStatus(), Response.Status.NO_CONTENT.getStatusCode());

        ClientResponse getPostDeleteResponse = client().resource("/v1/catalog/entities/"+symbolicName+"/"+TEST_VERSION)
                .get(ClientResponse.class);
        assertEquals(getPostDeleteResponse.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testSetDeprecated() {
        runSetDeprecated(DeprecateStyle.NEW_STYLE);
    }
    
    // Uses old-style "/v1/catalog/{itemId}/deprecated/true", rather than the "true" in the request body.
    @Test
    @Deprecated
    public void testSetDeprecatedLegacy() {
        runSetDeprecated(DeprecateStyle.LEGACY_STYLE);
    }

    protected void runSetDeprecated(DeprecateStyle style) {
        String symbolicName = "my.catalog.item.id.for.deprecation";
        String serviceType = "org.apache.brooklyn.entity.stock.BasicApplication";
        addTestCatalogItem(symbolicName, "template", TEST_VERSION, serviceType);
        addTestCatalogItem(symbolicName, "template", "2.0", serviceType);
        try {
            List<CatalogEntitySummary> applications = client().resource("/v1/catalog/applications")
                    .queryParam("fragment", symbolicName).queryParam("allVersions", "true").get(new GenericType<List<CatalogEntitySummary>>() {});
            assertEquals(applications.size(), 2);
            CatalogItemSummary summary0 = applications.get(0);
            CatalogItemSummary summary1 = applications.get(1);
    
            // Deprecate: that app should be excluded
            deprecateCatalogItem(style, summary0.getSymbolicName(), summary0.getVersion(), true);
    
            List<CatalogEntitySummary> applicationsAfterDeprecation = client().resource("/v1/catalog/applications")
                    .queryParam("fragment", "basicapp").queryParam("allVersions", "true").get(new GenericType<List<CatalogEntitySummary>>() {});
    
            assertEquals(applicationsAfterDeprecation.size(), 1);
            assertTrue(applicationsAfterDeprecation.contains(summary1));
    
            // Un-deprecate: that app should be included again
            deprecateCatalogItem(style, summary0.getSymbolicName(), summary0.getVersion(), false);
    
            List<CatalogEntitySummary> applicationsAfterUnDeprecation = client().resource("/v1/catalog/applications")
                    .queryParam("fragment", "basicapp").queryParam("allVersions", "true").get(new GenericType<List<CatalogEntitySummary>>() {});
    
            assertEquals(applications, applicationsAfterUnDeprecation);
        } finally {
            client().resource("/v1/catalog/entities/"+symbolicName+"/"+TEST_VERSION)
                    .delete(ClientResponse.class);
            client().resource("/v1/catalog/entities/"+symbolicName+"/"+"2.0")
                    .delete(ClientResponse.class);
        }
    }

    @Test
    public void testSetDisabled() {
        String symbolicName = "my.catalog.item.id.for.disabling";
        String serviceType = "org.apache.brooklyn.entity.stock.BasicApplication";
        addTestCatalogItem(symbolicName, "template", TEST_VERSION, serviceType);
        addTestCatalogItem(symbolicName, "template", "2.0", serviceType);
        try {
            List<CatalogEntitySummary> applications = client().resource("/v1/catalog/applications")
                    .queryParam("fragment", symbolicName).queryParam("allVersions", "true").get(new GenericType<List<CatalogEntitySummary>>() {});
            assertEquals(applications.size(), 2);
            CatalogItemSummary summary0 = applications.get(0);
            CatalogItemSummary summary1 = applications.get(1);
    
            // Disable: that app should be excluded
            disableCatalogItem(summary0.getSymbolicName(), summary0.getVersion(), true);
    
            List<CatalogEntitySummary> applicationsAfterDisabled = client().resource("/v1/catalog/applications")
                    .queryParam("fragment", "basicapp").queryParam("allVersions", "true").get(new GenericType<List<CatalogEntitySummary>>() {});
    
            assertEquals(applicationsAfterDisabled.size(), 1);
            assertTrue(applicationsAfterDisabled.contains(summary1));
    
            // Un-disable: that app should be included again
            disableCatalogItem(summary0.getSymbolicName(), summary0.getVersion(), false);
    
            List<CatalogEntitySummary> applicationsAfterUnDisabled = client().resource("/v1/catalog/applications")
                    .queryParam("fragment", "basicapp").queryParam("allVersions", "true").get(new GenericType<List<CatalogEntitySummary>>() {});
    
            assertEquals(applications, applicationsAfterUnDisabled);
        } finally {
            client().resource("/v1/catalog/entities/"+symbolicName+"/"+TEST_VERSION)
                    .delete(ClientResponse.class);
            client().resource("/v1/catalog/entities/"+symbolicName+"/"+"2.0")
                    .delete(ClientResponse.class);
        }
    }

    @Test
    public void testAddUnreachableItem() {
        addInvalidCatalogItem("http://0.0.0.0/can-not-connect");
    }

    @Test
    public void testAddInvalidItem() {
        //equivalent to HTTP response 200 text/html
        addInvalidCatalogItem("classpath://not-a-jar-file.txt");
    }

    @Test
    public void testAddMissingItem() {
        //equivalent to HTTP response 404 text/html
        addInvalidCatalogItem("classpath://missing-jar-file.txt");
    }

    private void addInvalidCatalogItem(String bundleUrl) {
        String symbolicName = "my.catalog.entity.id";
        String yaml =
                "brooklyn.catalog:\n"+
                "  id: " + symbolicName + "\n"+
                "  name: My Catalog App\n"+
                "  description: My description\n"+
                "  icon_url: classpath:/org/apache/brooklyn/test/osgi/entities/icon.gif\n"+
                "  version: " + TEST_VERSION + "\n"+
                "  libraries:\n"+
                "  - url: " + bundleUrl + "\n"+
                "\n"+
                "services:\n"+
                "- type: org.apache.brooklyn.core.test.entity.TestEntity\n";

        ClientResponse response = client().resource("/v1/catalog")
                .post(ClientResponse.class, yaml);

        assertEquals(response.getStatus(), HttpStatus.INTERNAL_SERVER_ERROR_500);
    }

    private static String ver(String id) {
        return CatalogUtils.getVersionedId(id, TEST_VERSION);
    }
}
