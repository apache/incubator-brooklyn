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

import com.google.common.collect.Maps;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public class EntitySummaryTest {

  static final Map<String, URI> links;
  static {
    links = Maps.newLinkedHashMap();
    links.put("self", URI.create("/v1/applications/tesr/entities/zQsqdXzi"));
    links.put("catalog", URI.create("/v1/catalog/entities/brooklyn.entity.webapp.tomcat.TomcatServer"));
    links.put("application", URI.create("/v1/applications/tesr"));
    links.put("children", URI.create("/v1/applications/tesr/entities/zQsqdXzi/entities"));
    links.put("effectors", URI.create("fixtures/effector-summary-list.json"));
    links.put("sensors", URI.create("fixtures/sensor-summary-list.json"));
    links.put("activities", URI.create("fixtures/task-summary-list.json"));
  }

  static final EntitySummary entitySummary = new EntitySummary(
          "zQsqdXzi", "MyTomcat", "brooklyn.entity.webapp.tomcat.TomcatServer", links);

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(entitySummary), jsonFixture("fixtures/entity-summary.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/entity-summary.json"), EntitySummary.class), entitySummary);
  }

}
