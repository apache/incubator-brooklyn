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

import brooklyn.rest.domain.EffectorSummary;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import java.net.URI;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class EffectorSummaryTest {

  final EffectorSummary effectorSummary = new EffectorSummary(
      "stop",
      "void",
      ImmutableSet.<EffectorSummary.ParameterSummary<?>>of(),
      "Effector description",
      ImmutableMap.of(
          "self", URI.create("/v1/applications/redis-app/entities/redis-ent/effectors/stop")
      )
  );

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(effectorSummary), jsonFixture("fixtures/effector-summary.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/effector-summary.json"), EffectorSummary.class), effectorSummary);
  }
}
