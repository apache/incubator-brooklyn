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
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.reporters.Files;

import brooklyn.catalog.CatalogItem;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.util.collections.MutableList;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

public class CatalogResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogResourceTest.class);

    @BeforeClass(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        useLocalScannedCatalog();
        super.setUp();
    }
    
  @Override
  protected void setUpResources() throws Exception {
    addResource(new CatalogResource());
  }

  @Test
  /** based on CampYamlLiteTest */
  public void testRegisterCustomEntityWithBundleWhereEntityIsFromCoreAndIconFromBundle() {
    String registeredTypeName = "my.catalog.app.id";
    String bundleUrl = OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL;
    String yaml =
        "brooklyn.catalog:\n"+
        "  id: " + registeredTypeName + "\n"+
        "  name: My Catalog App\n"+
        "  description: My description\n"+
        "  icon_url: classpath:/brooklyn/osgi/tests/icon.gif\n"+
        "  version: 0.1.2\n"+
        "  libraries:\n"+
        "  - url: " + bundleUrl + "\n"+
        "\n"+
        "services:\n"+
        "- type: brooklyn.test.entity.TestEntity\n";

    ClientResponse response = client().resource("/v1/catalog")
        .post(ClientResponse.class, yaml);

    assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

    CatalogEntitySummary entityItem = client().resource("/v1/catalog/entities/"+registeredTypeName)
            .get(CatalogEntitySummary.class);

    assertEquals(entityItem.getRegisteredType(), registeredTypeName);
    
    // stored as yaml, not java
//    assertEquals(entityItem.getJavaType(), "brooklyn.test.entity.TestEntity");
    Assert.assertNotNull(entityItem.getPlanYaml());
    Assert.assertTrue(entityItem.getPlanYaml().contains("brooklyn.test.entity.TestEntity"));
    
    assertEquals(entityItem.getId(), registeredTypeName);
    
    // and internally let's check we have libraries
    CatalogItem<?, ?> item = getManagementContext().getCatalog().getCatalogItem(registeredTypeName);
    Assert.assertNotNull(item);
    List<String> libs = item.getLibraries().getBundles();
    assertEquals(libs, MutableList.of(bundleUrl));

    // now let's check other things on the item
    assertEquals(entityItem.getName(), "My Catalog App");
    assertEquals(entityItem.getDescription(), "My description");
    assertEquals(entityItem.getIconUrl(), "/v1/catalog/icon/my.catalog.app.id");
    assertEquals(item.getIconUrl(), "classpath:/brooklyn/osgi/tests/icon.gif");
    
    byte[] iconData = client().resource("/v1/catalog/icon/"+registeredTypeName).get(byte[].class);
    assertEquals(iconData.length, 43);
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

  private static final String REDIS_STORE_ICON_URL = "/v1/catalog/icon/brooklyn.entity.nosql.redis.RedisStore";
  
  @Test
  public void testGetCatalogEntityDetails() {
      CatalogEntitySummary details = client().resource(
              URI.create("/v1/catalog/entities/brooklyn.entity.nosql.redis.RedisStore"))
              .get(CatalogEntitySummary.class);
      assertTrue(details.toString().contains("redis.port"), "expected more config, only got: "+details);
      assertTrue(details.getIconUrl().contains(REDIS_STORE_ICON_URL), "expected brooklyn URL for icon image, but got: "+details.getIconUrl());
  }

  @Test
  public void testGetCatalogEntityIconDetails() throws IOException {
      ClientResponse response = client().resource(
              URI.create(REDIS_STORE_ICON_URL)).get(ClientResponse.class);
      response.bufferEntity();
      Assert.assertEquals(response.getType(), MediaType.valueOf("image/png"));
      Image image = Toolkit.getDefaultToolkit().createImage(Files.readFile(response.getEntityInputStream()));
      Assert.assertNotNull(image);
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
    String registeredTypeName = "my.catalog.app.id.to.subsequently.delete";
    String yaml =
        "name: "+registeredTypeName+"\n"+
        // FIXME name above should be unnecessary when brooklyn.catalog below is working
        "brooklyn.catalog:\n"+
        "  id: " + registeredTypeName + "\n"+
        "  name: My Catalog App To Be Deleted\n"+
        "  description: My description\n"+
        "  version: 0.1.2\n"+
        "\n"+
        "services:\n"+
        "- type: brooklyn.test.entity.TestEntity\n";

    client().resource("/v1/catalog")
            .post(ClientResponse.class, yaml);
    
    ClientResponse deleteResponse = client().resource("/v1/catalog/entities/"+registeredTypeName)
            .delete(ClientResponse.class);

    assertEquals(deleteResponse.getStatus(), Response.Status.NO_CONTENT.getStatusCode());

    ClientResponse getPostDeleteResponse = client().resource("/v1/catalog/entities/"+registeredTypeName)
            .get(ClientResponse.class);
    assertEquals(getPostDeleteResponse.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
  }
}
