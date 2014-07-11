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

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Objects;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Adam Lowe
 */
public class UsageStatistic implements HasId {
    private final Status status;
    private final String id;
    private final String applicationId;
    private final String start;
    private final String end;
    private final long duration;
    private final Map<String,String> metadata;

    public UsageStatistic(@JsonProperty("status") Status status, @JsonProperty("id") String id, @JsonProperty("applicationId") String applicationId,
                     @JsonProperty("start") String start,
                     @JsonProperty("end") String end,
                     @JsonProperty("duration") long duration, @JsonProperty("metadata") Map<String, String> metadata) {
        this.status = checkNotNull(status, "status");
        this.id = checkNotNull(id, "id");
        this.applicationId = applicationId;
        this.start = start;
        this.end = end;
        this.duration = duration;
        this.metadata = checkNotNull(metadata, "metadata");
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public long getDuration() {
        return duration;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsageStatistic statistic = (UsageStatistic) o;

        return Objects.equal(status, statistic.status) &&
                Objects.equal(id, statistic.id) &&
                Objects.equal(applicationId, statistic.applicationId) &&
                Objects.equal(start, statistic.start) &&
                Objects.equal(end, statistic.end) &&
                Objects.equal(metadata, statistic.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(status, id, applicationId, start, end, metadata);
    }

    @Override
    public String toString() {
        return "UsageStatistic{" +
                "status=" + status +
                ", id='" + id + '\'' +
                ", applicationId='" + applicationId + '\'' +
                ", start='" + start + '\'' +
                ", end='" + end + '\'' +
                ", duration=" + duration +
                ", metadata=" + metadata +
                '}';
    }
}
