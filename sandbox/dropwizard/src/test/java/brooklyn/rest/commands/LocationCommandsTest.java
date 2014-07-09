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
import static org.hamcrest.Matchers.containsString;

import org.testng.annotations.Test;

import brooklyn.rest.commands.locations.AddLocationCommand;
import brooklyn.rest.commands.locations.ListLocationsCommand;
import brooklyn.rest.resources.LocationResource;

public class LocationCommandsTest extends BrooklynCommandTest {

  @Override
  protected void setUpResources() throws Exception {
    addResource(new LocationResource());
  }

  @Test
  public void testListLocations() throws Exception {
    runCommandWithArgs(ListLocationsCommand.class);

    assertThat(standardOut(), containsString("localhost"));
  }

  @Test
  public void testAddLocation() throws Exception {
    runCommandWithArgs(AddLocationCommand.class,
        createTemporaryFileWithContent(".json", "{\"provider\":\"localhost\", \"config\":{}}"));

    assertThat(standardOut(), containsString("http://localhost:8080/v1/locations/1"));
  }
}
