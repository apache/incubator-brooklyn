/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.collect.Maps;

public class BrooklynFeatureSummary implements Serializable {

    private static final long serialVersionUID = 4595452639602650453L;

    private final String name;
    private final String symbolicName;
    private final String version;
    private final String lastModified;
    private Map<String, String> additionalData = Maps.newHashMap();

    public BrooklynFeatureSummary(
                @JsonProperty("name") String name,
                @JsonProperty("symbolicName") String symbolicName,
                @JsonProperty("version") String version,
                @JsonProperty("lastModified") String lastModified) {
        this.symbolicName = checkNotNull(symbolicName, "symbolicName");
        this.name = name;
        this.version = version;
        this.lastModified = lastModified;
    }

    public BrooklynFeatureSummary(String name, String symbolicName, String version, String lastModified, Map<String, String> additionalData) {
        this(name, symbolicName, version, lastModified);
        this.additionalData = additionalData;
    }

    @JsonIgnore
    public Map<String, String> getAdditionalData() {
        return additionalData;
    }

    public String getLastModified() {
        return lastModified;
    }

    public String getName() {
        return name;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getVersion() {
        return version;
    }

    @JsonAnyGetter
    private Map<String, String> any() {
        return additionalData;
    }

    @JsonAnySetter
    private void set(String name, String value) {
        additionalData.put(name, value);
    }

}
