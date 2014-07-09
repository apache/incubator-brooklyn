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
package brooklyn.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.testng.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.cli.commands.CommandExecutionException;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.management.ManagementContext;
import brooklyn.policy.basic.GeneralPurposePolicy;
import brooklyn.rest.BrooklynService;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.yammer.dropwizard.logging.Log;

public abstract class ClientTest {

  private final static Log LOG = Log.forClass(ClientTest.class);

  protected BrooklynService brooklynServer;
  protected ManagementContext managementContext;
  protected Client brooklynClient;

  private ByteArrayOutputStream outBytes;
  private ByteArrayOutputStream errBytes;

  File tempConfigFile;

  protected String standardOut() {
    return outBytes.toString();
  }

  protected String standardErr() {
    return errBytes.toString();
  }

  public void oneTimeSetUp(String resourceConfigName) throws Exception {
    // Create temporary config file
    tempConfigFile = File.createTempFile("server-config", ".yml");
    tempConfigFile.deleteOnExit();

    InputStream configInputStream = getClass().getClassLoader()
        .getResourceAsStream(resourceConfigName);
    try {
      Files.write(ByteStreams.toByteArray(configInputStream), tempConfigFile);
    } finally {
      configInputStream.close();
    }

    // Start the REST server
    brooklynServer = BrooklynService.newBrooklynService();
    String[] args = {"server", tempConfigFile.getAbsolutePath()};
    brooklynServer.runAsync(args);

    managementContext = brooklynServer.getManagementContext();
  }

  public void oneTimeTearDown() throws Exception {
    brooklynServer.stop();
    tempConfigFile.delete();
  }

  @BeforeMethod
  public void setUp() throws Exception {
    outBytes = new ByteArrayOutputStream();
    errBytes = new ByteArrayOutputStream();

    brooklynClient = new Client(new PrintStream(outBytes), new PrintStream(errBytes));
  }

  protected void runWithArgs(String... args) throws Exception {
    List<String> argsWithEndpoint = Lists.newArrayList(
        "--endpoint", "http://localhost:60180"
    );
    argsWithEndpoint.addAll(Arrays.asList(args));

    brooklynClient.run(argsWithEndpoint.toArray(new String[argsWithEndpoint.size()]));
  }

  @Test
  public void testCatalogEntitiesCommand() throws Exception {
    try {
      runWithArgs("catalog-entities");

      // Check list of entity types includes one of the defaults
      assertThat(standardOut(), containsString(BasicEntity.class.getName()));
    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

  @Test
  public void testCatalogPoliciesCommand() throws Exception {
    try {
      runWithArgs("catalog-policies");

      // Check list of entity types includes one of the defaults
      assertThat(standardOut(), containsString(GeneralPurposePolicy.class.getName()));
    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

  @Test
  public void testDeployCreatesApp() throws Exception {
    try {
      runWithArgs("deploy", "--format", "class", "brooklyn.cli.ExampleApp");

      // We should only have 1 app in the server's registry
      assertEquals(managementContext.getApplications().size(), 1);

      // The name of that app should match what we have provided in the deploy command
      assertEquals(Iterables.getOnlyElement(managementContext.getApplications()).getEntityType().getName(), ExampleApp.class.getName());

    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

  @Test(dependsOnMethods = {"testDeployCreatesApp"})
  public void testListApplicationsShowsRunningApp() throws Exception {
    try {
      runWithArgs("list-applications");
      assertThat(standardOut(), containsString("brooklyn.cli.ExampleApp [RUNNING]"));

    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

  @Test(dependsOnMethods = {"testDeployCreatesApp"})
  public void testUndeployStopsRunningApp() throws Exception {
    try {
      runWithArgs("undeploy", "brooklyn.cli.ExampleApp");
      assertThat(standardOut(), containsString("Application has been undeployed: brooklyn.cli.ExampleApp"));

    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

  @Test(dependsOnMethods = {"testUndeployStopsRunningApp"}, expectedExceptions = {CommandExecutionException.class})
  public void testUndeployFailsGracefulyIfNoAppRunning() throws Exception {
    try {
      runWithArgs("undeploy", "brooklyn.cli.ExampleApp");
      assertThat(standardOut(), containsString("Application 'brooklyn.test.entity.TestApplication' not found"));

    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

  @Test(dependsOnMethods = {"testUndeployStopsRunningApp"})
  public void testListApplicationsNoRunningApp() throws Exception {
    try {
      runWithArgs("list-applications");
      assertEquals(standardOut(), "");

    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

  @Test
  public void testVersionCommand() throws Exception {
    try {
      runWithArgs("version");
      assertThat(standardOut(), containsString("Brooklyn version:"));

    } catch (Exception e) {
      LOG.error(e, "stdout={}; stderr={}", standardOut(), standardErr());
      throw e;
    }
  }

}
