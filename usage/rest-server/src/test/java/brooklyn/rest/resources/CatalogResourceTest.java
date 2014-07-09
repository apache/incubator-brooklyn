/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
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

import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

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

  @Test(enabled=false, groups="WIP")
  public void testRegisterCustomEntity() {
    String registeredTypeName = "my.catalog.app.id";
    String yaml =
        "name: "+registeredTypeName+"\n"+
        // FIXME name above should be unnecessary when brooklyn.catalog below is working
        "brooklyn.catalog:\n"+
        "- id: " + registeredTypeName + "\n"+
        "- name: My Catalog App\n"+
        "- description: My description\n"+
        "- icon_url: classpath://path/to/myicon.jpg\n"+
        "- version: 0.1.2\n"+
        "- brooklyn.libraries:\n"+
        "  - url: http://myurl/my.jar\n"+
        "\n"+
        "services:\n"+
        "- type: brooklyn.test.entity.TestEntity\n";

    ClientResponse response = client().resource("/v1/catalog")
        .post(ClientResponse.class, yaml);

    assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

    CatalogEntitySummary entityItem = client().resource("/v1/catalog/entities/"+registeredTypeName)
            .get(CatalogEntitySummary.class);
    // TODO more checks, when  brooklyn.catalog  working
//    assertEquals(entityItem.getId(), registeredTypeName);
//    assertEquals(entityItem.getName(), "My Catalog App");
//    assertEquals(entityItem.getDescription(), "My description");
//    assertEquals(entityItem.getIconUrl(), "classpath://path/to/myicon.jpg");
    assertEquals(entityItem.getRegisteredType(), registeredTypeName);
    assertEquals(entityItem.getJavaType(), "brooklyn.test.entity.TestEntity");
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
  
}
