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

import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

@Beta
public class AccessSummary implements Serializable {

    private static final long serialVersionUID = 5097292906225042890L;

    private final boolean locationProvisioningAllowed;
    private final Map<String, URI> links;

    public AccessSummary(
            @JsonProperty("locationProvisioningAllowed") boolean locationProvisioningAllowed,
            @JsonProperty("links") Map<String, URI> links
    ) {
        this.locationProvisioningAllowed = locationProvisioningAllowed;
        this.links = (links == null) ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
      }

    public boolean isLocationProvisioningAllowed() {
        return locationProvisioningAllowed;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessSummary that = (AccessSummary) o;
        return locationProvisioningAllowed == that.locationProvisioningAllowed &&
                Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationProvisioningAllowed, links);
    }

    @Override
    public String toString() {
        return "AccessSummary{" +
                "locationProvisioningAllowed='" + locationProvisioningAllowed + '\'' +
                ", links=" + links +
                '}';
    }
}
