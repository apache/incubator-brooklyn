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
package brooklyn.rest.commands;

import brooklyn.rest.commands.catalog.ListCatalogEntitiesCommand;
import brooklyn.rest.commands.catalog.ListCatalogPoliciesCommand;
import brooklyn.rest.commands.catalog.CatalogEntityDetailsCommand;
import brooklyn.rest.commands.catalog.LoadClassCommand;
import brooklyn.rest.resources.CatalogResource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import org.testng.annotations.Test;

public class CatalogCommandsTest extends BrooklynCommandTest {

  @Override
  protected void setUpResources() throws Exception {
    addResource(new CatalogResource());
  }

  @Test
  public void testListCatalogEntities() throws Exception {
    runCommandWithArgs(ListCatalogEntitiesCommand.class);

    assertThat(standardOut(), containsString("brooklyn.entity.webapp.jboss.JBoss6Server"));
  }

  @Test
  public void testListCatalogPolicies() throws Exception {
    runCommandWithArgs(ListCatalogPoliciesCommand.class);

    assertThat(standardOut(), containsString("brooklyn.policy.followthesun.FollowTheSunPolicy"));
  }

  @Test
  public void testCatalogEntityDetails() throws Exception {
    runCommandWithArgs(CatalogEntityDetailsCommand.class, "brooklyn.entity.nosql.redis.RedisStore");

    assertThat(standardOut(), containsString("redis.port"));
  }

  @Test
  public void testMissingConfigKeyNameForEntity() throws Exception {
    runCommandWithArgs(CatalogEntityDetailsCommand.class);

    assertThat(standardErr(), containsString("The type of the entity is mandatory"));
  }

  @Test
  public void testEntityTypeNotFound() throws Exception {
    runCommandWithArgs(CatalogEntityDetailsCommand.class, "dummy-entity-name");

    assertThat(standardErr(), containsString("Client response status: 404"));
  }

  @Test
  public void testUploadGroovyScriptToCreateEntity() throws Exception {
    String groovyScript = "package brooklyn.rest.entities.cli.custom\n" +
        "" +
        "import brooklyn.entity.basic.AbstractEntity\n" +
        "import brooklyn.entity.Entity\n" +
        "import brooklyn.event.basic.BasicConfigKey\n" +
        "" +
        "class DummyEntity extends AbstractEntity {\n" +
        "  public static final BasicConfigKey<String> DUMMY_CFG = [ String, \"dummy.config\", \"Dummy Config\" ]\n" +
        "  public DummyEntity(Map properties=[:], Entity owner=null) {\n" +
        "        super(properties, owner)" +
        "  }" +
        "}\n";

    runCommandWithArgs(LoadClassCommand.class,
        createTemporaryFileWithContent(".groovy", groovyScript));

    assertThat(standardOut(), containsString("http://localhost:8080/v1/catalog/entities" +
        "/brooklyn.rest.entities.cli.custom.DummyEntity"));
  }


}
