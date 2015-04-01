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
package foghorn.rest.domain;

import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.HasId;
import brooklyn.rest.domain.Status;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;
import java.util.Map;

public class DeploymentSummary implements HasId {

    @JsonIgnore
    private ApplicationSummary applicationSummary;

    public DeploymentSummary(
            @JsonProperty("id") String id,
            @JsonProperty("spec") ApplicationSpec spec,
            @JsonProperty("status") Status status,
            @JsonProperty("links") Map<String, URI> links
    ) {
        this.applicationSummary = new ApplicationSummary(id, spec, status, links);
        applicationSummary.getId();
        applicationSummary.getLinks();
        applicationSummary.getSpec();
        applicationSummary.getStatus();
    }

    @JsonProperty
    public ApplicationSpec getSpec() {
        return applicationSummary.getSpec();
    }

    @JsonProperty
    public Map<String, URI> getLinks() {
        return applicationSummary.getLinks();
    }

    @JsonProperty
    public Status getStatus() {
        return applicationSummary.getStatus();
    }

    @Override
    public boolean equals(Object o) {
        return applicationSummary.equals(o);
    }

    @Override
    public int hashCode() {
        return applicationSummary.hashCode();
    }

    @Override
    public String toString() {
        return "Deployment<" + applicationSummary.toString() + ">";
    }

    @Override
    @JsonProperty
    public String getId() {
        return applicationSummary.getId();
    }
}
