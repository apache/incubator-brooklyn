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

import com.google.common.collect.ImmutableMap;

import org.codehaus.jackson.annotate.JsonProperty;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class EntitySpec implements HasName, Serializable {

    private static final long serialVersionUID = -3882575609132757188L;
    
    private final String name;
    private final String type;
    private final Map<String, String> config;

    public EntitySpec(String type) {
        this(null, type);
    }

    public EntitySpec(String name, String type) {
        this(name, type, Collections.<String, String> emptyMap());
    }

    public EntitySpec(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("config") Map<String, String> config) {
        this.type = checkNotNull(type, "type");
        this.name = (name == null) ? type : name;
        this.config = (config != null) ? ImmutableMap.copyOf(config) : ImmutableMap.<String, String> of();
    }

    @Override
    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EntitySpec entitySpec = (EntitySpec) o;

        if (config != null ? !config.equals(entitySpec.config) : entitySpec.config != null)
            return false;
        if (name != null ? !name.equals(entitySpec.name) : entitySpec.name != null)
            return false;
        if (type != null ? !type.equals(entitySpec.type) : entitySpec.type != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (config != null ? config.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "EntitySpec{"
                + "name='" + name + '\''
                + ", type='" + type + '\''
                + ", config=" + config
                + '}';
    }
}
