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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.codehaus.jackson.annotate.JsonProperty;

public class VersionSummary implements Serializable {

    private static final long serialVersionUID = 7275038546963638540L;
    
    private final String version;
    private final String buildSha1;
    private final String buildBranch;
    private final List<BrooklynFeatureSummary> features;

    public VersionSummary(String version) {
        this(version, null, null);
    }

    public VersionSummary(String version, String buildSha1, String buildBranch) {
        this(version, buildSha1, buildBranch, Collections.<BrooklynFeatureSummary>emptyList());
    }

    public VersionSummary(
            @JsonProperty("version") String version,
            @JsonProperty("buildSha1") String buildSha1,
            @JsonProperty("buildBranch") String buildBranch,
            @JsonProperty("features") List<BrooklynFeatureSummary> features) {
        this.version = checkNotNull(version, "version");
        this.buildSha1 = buildSha1;
        this.buildBranch = buildBranch;
        this.features = checkNotNull(features, "features");
    }

    @Nonnull
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getBuildSha1() {
        return buildSha1;
    }

    @Nullable
    public String getBuildBranch() {
        return buildBranch;
    }

    @Nonnull
    public List<BrooklynFeatureSummary> getFeatures() {
        return features;
    }

}
