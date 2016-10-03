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
import com.google.common.collect.ImmutableMap;

public class HighAvailabilitySummary implements Serializable {

    private static final long serialVersionUID = -317333127094471223L;

    public static class HaNodeSummary implements Serializable {
        private static final long serialVersionUID = 9205960988226816539L;

        private final String nodeId;
        private final URI nodeUri;
        private final String status;
        private final Long localTimestamp;
        private final Long remoteTimestamp;

        public HaNodeSummary(
                @JsonProperty("nodeId") String nodeId,
                @JsonProperty("nodeUri") URI nodeUri,
                @JsonProperty("status") String status,
                @JsonProperty("localTimestamp") Long localTimestamp,
                @JsonProperty("remoteTimestamp") Long remoteTimestamp) {
            this.nodeId = nodeId;
            this.nodeUri = nodeUri;
            this.status = status;
            this.localTimestamp = localTimestamp;
            this.remoteTimestamp = remoteTimestamp;
        }

        public String getNodeId() {
            return nodeId;
        }

        public URI getNodeUri() {
            return nodeUri;
        }

        public String getStatus() {
            return status;
        }

        public Long getLocalTimestamp() {
            return localTimestamp;
        }

        public Long getRemoteTimestamp() {
            return remoteTimestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HaNodeSummary that = (HaNodeSummary) o;
            return Objects.equals(nodeId, that.nodeId) &&
                    Objects.equals(nodeUri, that.nodeUri) &&
                    Objects.equals(status, that.status) &&
                    Objects.equals(localTimestamp, that.localTimestamp) &&
                    Objects.equals(remoteTimestamp, that.remoteTimestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, nodeUri, status, localTimestamp, remoteTimestamp);
        }

        @Override
        public String toString() {
            return "HighAvailabilitySummary{"
                    + "nodeId='" + nodeId + '\''
                    + ", status='" + status + '\''
                    + '}';
        }
    }

    private final String ownId;
    private final String masterId;
    private final Map<String, HaNodeSummary> nodes;
    private final Map<String, URI> links;

    public HighAvailabilitySummary(
            @JsonProperty("ownId") String ownId,
            @JsonProperty("masterId") String masterId,
            @JsonProperty("nodes") Map<String, HaNodeSummary> nodes,
            @JsonProperty("links") Map<String, URI> links) {
        this.ownId = ownId;
        this.masterId = masterId;
        this.nodes = (nodes == null) ? ImmutableMap.<String, HaNodeSummary>of() : nodes;
        this.links = (links == null) ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
    }

    public String getOwnId() {
        return ownId;
    }

    public String getMasterId() {
        return masterId;
    }

    public Map<String, HaNodeSummary> getNodes() {
        return nodes;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HighAvailabilitySummary that = (HighAvailabilitySummary) o;

        if (ownId != null ? !ownId.equals(that.ownId) : that.ownId != null) return false;
        if (masterId != null ? !masterId.equals(that.masterId) : that.masterId != null) return false;
        if (nodes != null ? !nodes.equals(that.nodes) : that.nodes != null) return false;
        return links != null ? links.equals(that.links) : that.links == null;

    }

    @Override
    public int hashCode() {
        int result = ownId != null ? ownId.hashCode() : 0;
        result = 31 * result + (masterId != null ? masterId.hashCode() : 0);
        result = 31 * result + (nodes != null ? nodes.hashCode() : 0);
        result = 31 * result + (links != null ? links.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "HighAvailabilitySummary{" +
                "ownId='" + ownId + '\'' +
                ", masterId='" + masterId + '\'' +
                ", nodes=" + nodes +
                ", links=" + links +
                '}';
    }
}
