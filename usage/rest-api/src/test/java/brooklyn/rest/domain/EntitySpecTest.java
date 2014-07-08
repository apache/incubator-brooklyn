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
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

import brooklyn.rest.domain.EntitySpec;

import java.io.IOException;

public class EntitySpecTest {

  final EntitySpec entitySpec = new EntitySpec("Vanilla Java App", "brooklyn.entity.java.VanillaJavaApp");

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(new EntitySpec[]{entitySpec}), jsonFixture("fixtures/entity.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/entity.json"), EntitySpec[].class), new EntitySpec[]{entitySpec});
  }

  @Test
  public void testDeserializeFromJSONOnlyWithType() throws IOException {
    EntitySpec actual = fromJson(jsonFixture("fixtures/entity-only-type.json"), EntitySpec.class);
    assertEquals(actual.getName(), actual.getType());
    assertEquals(actual.getConfig().size(), 0);
  }
}
