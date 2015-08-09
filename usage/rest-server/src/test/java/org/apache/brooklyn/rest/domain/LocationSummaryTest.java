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
package org.apache.brooklyn.rest.domain;

import static org.apache.brooklyn.rest.util.RestApiTestUtils.asJson;
import static org.apache.brooklyn.rest.util.RestApiTestUtils.fromJson;
import static org.apache.brooklyn.rest.util.RestApiTestUtils.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.codehaus.jackson.type.TypeReference;
import org.testng.annotations.Test;

import org.apache.brooklyn.rest.transform.LocationTransformer;

public class LocationSummaryTest {

    @SuppressWarnings("deprecation")
    final LocationSummary summary = LocationTransformer.newInstance("123", LocationSpec.localhost());

    @Test
    public void testSerializeToJSON() throws IOException {
        assertEquals(asJson(summary), jsonFixture("fixtures/location-summary.json"));
    }

    @Test
    public void testDeserializeFromJSON() throws IOException {
        assertEquals(fromJson(jsonFixture("fixtures/location-summary.json"), LocationSummary.class), summary);
    }

    @Test
    public void testDeserializeListFromJSON() throws IOException {
        assertEquals(fromJson(jsonFixture("fixtures/location-list.json"), new TypeReference<List<LocationSummary>>() {}), 
                Collections.singletonList(summary));
    }
}
