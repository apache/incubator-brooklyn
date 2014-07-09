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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import brooklyn.rest.commands.applications.DeleteApplicationCommand;
import brooklyn.rest.commands.applications.InvokeEffectorCommand;
import brooklyn.rest.commands.applications.ListApplicationsCommand;
import brooklyn.rest.commands.applications.ListEffectorsCommand;
import brooklyn.rest.commands.applications.QuerySensorsCommand;
import brooklyn.rest.commands.applications.StartApplicationCommand;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;

public class ApplicationCommandsTest extends BrooklynCommandTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCommandsTest.class);
    
  @Override
  protected void setUpResources() throws Exception {
      addResources();
  }

  @AfterClass
  public void tearDown() throws Exception {
    super.tearDownJersey();
  }

  @Test
  public void testStartApplication() throws Exception {
    String simpleRecipe = "{\"entities\":[\n" +
        "  {\n" +
        "    \"type\":\""+RestMockSimpleEntity.class.getName()+"\",\n" +
        "    \"config\": {\"sampleConfig\": \"hiya-world\"}\n" +
        "  }\n" +
        "],\n" +
        "  \"locations\":[\n" +
        "    \"/v1/locations/0\"\n" +
        "  ],\n" +
        "  \"name\":\"simple\"\n" +
        "}";
    runCommandWithArgs(StartApplicationCommand.class,
        createTemporaryFileWithContent(".json", simpleRecipe));

    assertThat(standardOut(), allOf(containsString("/v1/applications/simple"),
        containsString("Done")));
  }

  @Test(dependsOnMethods = "testStartApplication")
  public void testListApplications() throws Exception {
    runCommandWithArgs(ListApplicationsCommand.class);

    assertThat(standardOut(), allOf(containsString("simple"), containsString("RUNNING")));
  }

  @Test(dependsOnMethods = "testStartApplication")
  public void testListEffectors() throws Exception {
    runCommandWithArgs(ListEffectorsCommand.class, "simple");

    assertThat(standardOut(), allOf(
        containsString("/v1/applications/simple/entities/"),
        containsString("sampleEffector")
    ));
  }

  // TODO CLI to invoke effector **with arguments**
  @Test(enabled=false, dependsOnMethods = "testStartApplication")
  public void testInvokeEffectors() throws Exception {
    runCommandWithArgs(InvokeEffectorCommand.class, "simple", "sampleEffector", "foo", "2");

    assertThat(standardOut(), allOf(
        containsString("foo2")
    ));
  }

  // TODO requires effector above is invoked 
  @Test(enabled=false, dependsOnMethods = "testInvokeEffectors")
  public void testQuerySensors() throws Exception {
      runCommandWithArgs(QuerySensorsCommand.class, "simple");
      
      assertThat(standardOut(), allOf(
              containsString("/v1/applications/simple/entities/"),
              containsString(RestMockSimpleEntity.class.getName()),
              containsString("foo2")
              ));
  }
  
  // TODO when above re-enabled, depend on the following
//  @Test(dependsOnMethods = {"testListEffectors", "testQuerySensors", "testListApplications"})
  @Test(dependsOnMethods = {"testListEffectors", "testListApplications"})
  public void testDeleteApplication() throws Exception {
    runCommandWithArgs(DeleteApplicationCommand.class, "simple");

    assertThat(standardOut(), containsString("Ok, status: 202"));
  }

}
