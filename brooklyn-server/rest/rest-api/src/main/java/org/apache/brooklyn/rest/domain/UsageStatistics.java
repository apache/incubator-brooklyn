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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class UsageStatistics implements Serializable {

    private static final long serialVersionUID = -1842301852728290967L;

    // TODO populate links with /apps endpoint to link to /usage/applications/{id}, to make it more
    // RESTy

    private final List<UsageStatistic> statistics;
    private final Map<String, URI> links;

    public UsageStatistics(@JsonProperty("statistics") List<UsageStatistic> statistics,
                           @JsonProperty("links") Map<String, URI> links) {
        this.statistics = statistics == null ? ImmutableList.<UsageStatistic> of() : ImmutableList.copyOf(statistics);
        this.links = (links == null) ? ImmutableMap.<String, URI> of() : ImmutableMap.copyOf(links);
    }

    public List<UsageStatistic> getStatistics() {
        return statistics;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsageStatistics)) return false;
        UsageStatistics that = (UsageStatistics) o;
        return Objects.equals(statistics, that.statistics) &&
                Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statistics, links);
    }

    @Override
    public String toString() {
        return "UsageStatistics{" +
                "statistics=" + statistics +
                ", links=" + links +
                '}';
    }
}
