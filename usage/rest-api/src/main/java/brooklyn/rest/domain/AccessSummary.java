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

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

@Beta
public class AccessSummary {

    private final boolean locationProvisioningAllowed;
    private final Map<String, URI> links;

    public AccessSummary(
            @JsonProperty("locationProvisioningAllowed") boolean locationProvisioningAllowed,
            @JsonProperty("links") Map<String, URI> links
    ) {
        this.locationProvisioningAllowed = locationProvisioningAllowed;
        this.links = links == null ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
      }

    public boolean isLocationProvisioningAllowed() {
        return locationProvisioningAllowed;
    }

    public Map<String, URI> getLinks() {
        return links;
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AccessSummary)) return false;
        AccessSummary other = (AccessSummary) o;
        return locationProvisioningAllowed == other.isLocationProvisioningAllowed();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(locationProvisioningAllowed);
    }

    @Override
    public String toString() {
        return "AccessSummary{" +
                "locationProvisioningAllowed='" + locationProvisioningAllowed + '\'' +
                ", links=" + links +
                '}';
    }
}
