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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

public class ApplicationSummary implements HasId, Serializable {

    private static final long serialVersionUID = -247411021540729088L;

    private final static Map<Status, Status> validTransitions =
            ImmutableMap.<Status, Status>builder()
                    .put(Status.UNKNOWN, Status.ACCEPTED)
                    .put(Status.ACCEPTED, Status.STARTING)
                    .put(Status.STARTING, Status.RUNNING)
                    .put(Status.RUNNING, Status.STOPPING)
                    .put(Status.STOPPING, Status.STOPPED)
                    .put(Status.STOPPED, Status.STARTING)
                    .build();

    private final String id;
    private final ApplicationSpec spec;
    private final Status status;
    private final Map<String, URI> links;

    public ApplicationSummary(
            @JsonProperty("id") String id,
            @JsonProperty("spec") ApplicationSpec spec,
            @JsonProperty("status") Status status,
            @JsonProperty("links") Map<String, URI> links
    ) {
        this.id = id;
        this.spec = checkNotNull(spec, "spec");
        this.status = checkNotNull(status, "status");
        this.links = (links == null) ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
    }

    @Override
    public String getId() {
        return id;
    }

    public ApplicationSpec getSpec() {
        return spec;
    }

    public Status getStatus() {
        return status;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    public ApplicationSummary transitionTo(Status newStatus) {
        if (newStatus == Status.ERROR || validTransitions.get(status) == newStatus) {
            return new ApplicationSummary(id, spec, newStatus, links);
        }
        throw new IllegalStateException("Invalid transition from '" +
                status + "' to '" + newStatus + "'");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationSummary that = (ApplicationSummary) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(spec, that.spec) &&
                status == that.status &&
                Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, spec, status, links);
    }

    @Override
    public String toString() {
        return "Application{" +
                "id=" + id +
                ", spec=" + spec +
                ", status=" + status +
                '}';
    }

}
