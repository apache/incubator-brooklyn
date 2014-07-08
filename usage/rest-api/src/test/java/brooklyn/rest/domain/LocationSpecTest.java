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
package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import org.testng.annotations.Test;

import brooklyn.rest.domain.LocationSpec;

@Deprecated
public class LocationSpecTest {

    // TODO when removing the deprecated class this tests, change the tests here to point at LocationSummary
    
  final LocationSpec locationSpec = LocationSpec.localhost();

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(locationSpec), jsonFixture("fixtures/location.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/location.json"), LocationSpec.class), locationSpec);
  }

  @Test
  public void testDeserializeFromJSONWithNoCredential() throws IOException {
    LocationSpec loaded = fromJson(jsonFixture("fixtures/location-without-credential.json"), LocationSpec.class);

    assertEquals(loaded.getSpec(), locationSpec.getSpec());
    
    assertEquals(loaded.getConfig().size(), 1);
    assertEquals(loaded.getConfig().get("identity"), "bob");
    assertNull(loaded.getConfig().get("credential"));
  }
}
