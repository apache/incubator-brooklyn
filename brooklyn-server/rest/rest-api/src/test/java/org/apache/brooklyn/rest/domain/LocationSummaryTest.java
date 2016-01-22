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

import java.net.URI;
import java.util.Map;

import com.google.common.collect.Maps;

public class LocationSummaryTest extends AbstractDomainTest {

    @Override
    protected String getPath() {
        return "fixtures/location-summary.json";
    }

    @Override
    protected Object getDomainObject() {
        Map<String, URI> links = Maps.newLinkedHashMap();
        links.put("self", URI.create("/v1/locations/123"));

        return new LocationSummary("123", "localhost", "localhost", null, null, links);
    }

}
