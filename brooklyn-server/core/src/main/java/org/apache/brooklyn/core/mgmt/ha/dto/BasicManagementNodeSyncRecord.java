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
package org.apache.brooklyn.core.mgmt.ha.dto;

import java.io.Serializable;
import java.net.URI;

import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeSyncRecord;
import org.apache.brooklyn.core.BrooklynVersion;
import org.apache.brooklyn.util.time.Time;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

/**
 * Represents the state of a management node within the Brooklyn management plane
 * (DTO class).
 * 
 * @author aled
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE)
public class BasicManagementNodeSyncRecord implements ManagementNodeSyncRecord, Serializable {

    private static final long serialVersionUID = 4918161834047884244L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String brooklynVersion = BrooklynVersion.get();
        protected String nodeId;
        protected URI uri;
        protected ManagementNodeState status;
        protected Long priority;
        protected long localTimestamp;
        protected Long remoteTimestamp;

        protected Builder self() {
            return (Builder) this;
        }
        public Builder brooklynVersion(String val) {
            brooklynVersion = val; return self();
        }
        public Builder nodeId(String val) {
            nodeId = val; return self();
        }
        public Builder uri(URI val) {
            uri = val; return self();
        }
        public Builder status(ManagementNodeState val) {
            status = val; return self();
        }
        public Builder priority(Long val) {
            priority = val; return self();
        }
        public Builder localTimestamp(long val) {
            localTimestamp = val; return self();
        }
        public Builder remoteTimestamp(Long val) {
            remoteTimestamp = val; return self();
        }
        public Builder from(ManagementNodeSyncRecord other) {
            return from(other, false);
        }
        public Builder from(ManagementNodeSyncRecord other, boolean ignoreNulls) {
            if (ignoreNulls && other==null) return this;
            if (other.getBrooklynVersion()!=null) brooklynVersion = other.getBrooklynVersion();
            if (other.getNodeId()!=null) nodeId = other.getNodeId();
            if (other.getUri()!=null) uri = other.getUri();
            if (other.getStatus()!=null) status = other.getStatus();
            if (other.getPriority()!=null) priority = other.getPriority();
            if (other.getLocalTimestamp()>0) localTimestamp = other.getLocalTimestamp();
            if (other.getRemoteTimestamp()!=null) remoteTimestamp = other.getRemoteTimestamp();
            return this;
        }
        public ManagementNodeSyncRecord build() {
            return new BasicManagementNodeSyncRecord(this);
        }
    }
    
    private String brooklynVersion;
    private String nodeId;
    private URI uri;
    private ManagementNodeState status;
    private Long priority;
    private Long localTimestamp;
    private Long remoteTimestamp;
    
    /** @deprecated since 0.7.0, use {@link #localTimestamp} or {@link #remoteTimestamp},
     * but kept (or rather added back in) to support deserializing previous instances */
    @Deprecated
    private Long timestampUtc;


    // for de-serialization
    @SuppressWarnings("unused")
    private BasicManagementNodeSyncRecord() {
    }

    // Trusts the builder to not mess around with mutability concurrently with build().
    protected BasicManagementNodeSyncRecord(Builder builder) {
        brooklynVersion = builder.brooklynVersion;
        nodeId = builder.nodeId;
        uri = builder.uri;
        status = builder.status;
        priority = builder.priority;
        localTimestamp = builder.localTimestamp;
        remoteTimestamp = builder.remoteTimestamp;
    }

    @Override
    public String getBrooklynVersion() {
        return brooklynVersion;
    }
    
    @Override
    public String getNodeId() {
        return nodeId;
    }
    
    @Override
    public URI getUri() {
        return uri;
    }
    
    @Override
    public ManagementNodeState getStatus() {
        return status;
    }
    
    @Override
    public Long getPriority() {
        return priority;
    }
    
    @Override
    public long getLocalTimestamp() {
        if (localTimestamp!=null) return localTimestamp;
        if (timestampUtc!=null) return timestampUtc;
        throw new NullPointerException("localTimestamp not known for "+getNodeId());
    }
    
    @Override
    public Long getRemoteTimestamp() {
        return remoteTimestamp;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("nodeId", getNodeId())
                .add("status", getStatus()).toString();
    }
    
    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .add("brooklynVersion", getBrooklynVersion())
                .add("nodeId", getNodeId())
                .add("uri", getUri())
                .add("status", getStatus())
                .add("priority", getPriority())
                .add("localTimestamp", getLocalTimestamp()+"="+Time.makeDateString(getLocalTimestamp()))
                .add("remoteTimestamp", getRemoteTimestamp()+(getRemoteTimestamp()==null ? "" : 
                    "="+Time.makeDateString(getRemoteTimestamp())))
                .toString();
    }

    /** used here for store to inject remote timestamp */
    public void setRemoteTimestamp(Long remoteTimestamp) {
        this.remoteTimestamp = remoteTimestamp;
    }
    
}
