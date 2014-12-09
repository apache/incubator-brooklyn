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

import java.awt.Image;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import brooklyn.test.TestResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.reporters.Files;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogBundle;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.domain.CatalogPolicySummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

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
  public void testRegisterCustomEntityWithBundleWhereEntityIsFromCoreAndIconFromBundle() {
    TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

    String symbolicName = "my.catalog.entity.id";
    String bundleUrl = OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL;
    String yaml =
        "brooklyn.catalog:\n"+
        "  id: " + symbolicName + "\n"+
        "  name: My Catalog App\n"+
        "  description: My description\n"+
        "  icon_url: classpath:/brooklyn/osgi/tests/icon.gif\n"+
        "  version: " + TEST_VERSION + "\n"+
        "  libraries:\n"+
        "  - url: " + bundleUrl + "\n"+
        "\n"+
        "services:\n"+
        "- type: brooklyn.test.entity.TestEntity\n";

    ClientResponse response = client().resource("/v1/catalog")
        .post(ClientResponse.class, yaml);

    assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

    CatalogEntitySummary entityItem = client().resource("/v1/catalog/entities/"+symbolicName + "/" + TEST_VERSION)
            .get(CatalogEntitySummary.class);

    Assert.assertNotNull(entityItem.getPlanYaml());
    Assert.assertTrue(entityItem.getPlanYaml().contains("brooklyn.test.entity.TestEntity"));
    
    assertEquals(entityItem.getId(), ver(symbolicName));
    assertEquals(entityItem.getSymbolicName(), symbolicName);
    assertEquals(entityItem.getVersion(), TEST_VERSION);
    
    // and internally let's check we have libraries
    CatalogItem<?, ?> item = getManagementContext().getCatalog().getCatalogItem(symbolicName, TEST_VERSION);
    Assert.assertNotNull(item);
    Collection<CatalogBundle> libs = item.getLibraries();
    assertEquals(libs.size(), 1);
    assertEquals(Iterables.getOnlyElement(libs).getUrl(), bundleUrl);

    // now let's check other things on the item
    assertEquals(entityItem.getName(), "My Catalog App");
    assertEquals(entityItem.getDescription(), "My description");
    assertEquals(entityItem.getIconUrl(), "/v1/catalog/icon/" + symbolicName + "/" + entityItem.getVersion());
    assertEquals(item.getIconUrl(), "classpath:/brooklyn/osgi/tests/icon.gif");
    
    byte[] iconData = client().resource("/v1/catalog/icon/"+symbolicName + "/" + TEST_VERSION).get(byte[].class);
    assertEquals(iconData.length, 43);
  }

  @Test
  public void testRegisterOSGiPolicy() {
    TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

    String symbolicName = "my.catalog.policy.id";
    String policyType = "brooklyn.osgi.tests.SimplePolicy";
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

    CatalogPolicySummary entityItem = client().resource("/v1/catalog")
        .post(CatalogPolicySummary.class, yaml);

    Assert.assertNotNull(entityItem.getPlanYaml());
    Assert.assertTrue(entityItem.getPlanYaml().contains(policyType));
    assertEquals(entityItem.getId(), ver(symbolicName));
    assertEquals(entityItem.getSymbolicName(), symbolicName);
    assertEquals(entityItem.getVersion(), TEST_VERSION);
  }

  @Test
  public void testListAllEntities() {
    List<CatalogItemSummary> entities = client().resource("/v1/catalog/entities")
        .get(new GenericType<List<CatalogItemSummary>>() {
        });
    assertTrue(entities.size() > 0);
  }

  @Test
  public void testFilterListOfEntitiesByName() {
    List<CatalogItemSummary> entities = client().resource("/v1/catalog/entities")
            .queryParam("fragment", "reDISclusTER").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities.size(), 1);

    log.info("RedisCluster-like entities are: "+entities);
    
    List<CatalogItemSummary> entities2 = client().resource("/v1/catalog/entities")
            .queryParam("regex", "[Rr]ed.[sulC]+ter").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities2.size(), 1);
    
    assertEquals(entities, entities2);
    
    List<CatalogItemSummary> entities3 = client().resource("/v1/catalog/entities")
            .queryParam("fragment", "bweqQzZ").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities3.size(), 0);
    
    List<CatalogItemSummary> entities4 = client().resource("/v1/catalog/entities")
            .queryParam("regex", "bweq+z+").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities4.size(), 0);
  }

  @Test
  @Deprecated
  //If we move to using a yaml catalog item, the details will be of the wrapping app,
  //not of the entity itself, so the test won't make sense any more.
  public void testGetCatalogEntityDetails() {
      CatalogEntitySummary details = client().resource(
              URI.create("/v1/catalog/entities/brooklyn.entity.nosql.redis.RedisStore"))
              .get(CatalogEntitySummary.class);
      assertTrue(details.toString().contains("redis.port"), "expected more config, only got: "+details);
      String iconUrl = "/v1/catalog/icon/" + details.getSymbolicName();
      assertTrue(details.getIconUrl().contains(iconUrl), "expected brooklyn URL for icon image, but got: "+details.getIconUrl());
  }

  @Test
  @Deprecated
  //If we move to using a yaml catalog item, the details will be of the wrapping app,
  //not of the entity itself, so the test won't make sense any more.
  public void testGetCatalogEntityPlusVersionDetails() {
      CatalogEntitySummary details = client().resource(
              URI.create("/v1/catalog/entities/brooklyn.entity.nosql.redis.RedisStore:0.0.0.SNAPSHOT"))
              .get(CatalogEntitySummary.class);
      assertTrue(details.toString().contains("redis.port"), "expected more config, only got: "+details);
      String expectedIconUrl = "/v1/catalog/icon/" + details.getSymbolicName() + "/" + details.getVersion();
      assertEquals(details.getIconUrl(), expectedIconUrl, "expected brooklyn URL for icon image ("+expectedIconUrl+"), but got: "+details.getIconUrl());
  }

  @Test
  public void testGetCatalogEntityIconDetails() throws IOException {
      String catalogItemId = "testGetCatalogEntityIconDetails";
      addTestCatalogItem(catalogItemId);
      ClientResponse response = client().resource(URI.create("/v1/catalog/icon/" + catalogItemId + "/" + TEST_VERSION))
              .get(ClientResponse.class);
      response.bufferEntity();
      Assert.assertEquals(response.getStatus(), 200);
      Assert.assertEquals(response.getType(), MediaType.valueOf("image/png"));
      Image image = Toolkit.getDefaultToolkit().createImage(Files.readFile(response.getEntityInputStream()));
      Assert.assertNotNull(image);
  }

  private void addTestCatalogItem(String catalogItemId) {
      String yaml =
              "brooklyn.catalog:\n"+
              "  id: " + catalogItemId + "\n"+
              "  name: My Catalog App\n"+
              "  description: My description\n"+
              "  icon_url: classpath:///redis-logo.png\n"+
              "  version: " + TEST_VERSION + "\n"+
              "\n"+
              "services:\n"+
              "- type: brooklyn.entity.nosql.redis.RedisStore\n";

      client().resource("/v1/catalog").post(yaml);
  }

  @Test
  public void testListPolicies() {
    Set<CatalogItemSummary> policies = client().resource("/v1/catalog/policies")
        .get(new GenericType<Set<CatalogItemSummary>>() {
        });

    assertTrue(policies.size() > 0);
    CatalogItemSummary asp = null;
    for (CatalogItemSummary p: policies) {
        if (AutoScalerPolicy.class.getName().equals(p.getType()))
            asp = p;
    }
    Assert.assertNotNull(asp, "didn't find AutoScalerPolicy");
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
        "- type: brooklyn.test.entity.TestEntity\n";

    client().resource("/v1/catalog")
            .post(ClientResponse.class, yaml);
    
    ClientResponse deleteResponse = client().resource("/v1/catalog/entities/"+symbolicName+"/"+TEST_VERSION)
            .delete(ClientResponse.class);

    assertEquals(deleteResponse.getStatus(), Response.Status.NO_CONTENT.getStatusCode());

    ClientResponse getPostDeleteResponse = client().resource("/v1/catalog/entities/"+symbolicName+"/"+TEST_VERSION)
            .get(ClientResponse.class);
    assertEquals(getPostDeleteResponse.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
  }
  
  private static String ver(String id) {
      return CatalogUtils.getVersionedId(id, TEST_VERSION);
  }
}
