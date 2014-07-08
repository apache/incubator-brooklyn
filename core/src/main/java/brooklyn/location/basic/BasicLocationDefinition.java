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
package brooklyn.location.basic;

import java.util.Map;

import brooklyn.location.LocationDefinition;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class BasicLocationDefinition implements LocationDefinition {

    private final String id;
    private final String name;
    private final String spec;
    private final Map<String,Object> config;

    public BasicLocationDefinition(String name, String spec, Map<String,? extends Object> config) {
        this(Identifiers.makeRandomId(8), name, spec, config);
    }
    
    public BasicLocationDefinition(String id, String name, String spec, Map<String,? extends Object> config) {      
        this.id = Preconditions.checkNotNull(id);
        this.name = name;
        this.spec = Preconditions.checkNotNull(spec);
        this.config = config==null ? ImmutableMap.<String, Object>of() : ImmutableMap.<String, Object>copyOf(config);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    public String getSpec() {
        return spec;
    }
    
    @Override
    public Map<String, Object> getConfig() {
        return config;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this==o) return true;
        if ((o instanceof LocationDefinition) && id.equals(((LocationDefinition)o).getId())) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LocationDefinition{" +
                "id='" + getId() + '\'' +
                ", name='" + getName() + '\'' +
                ", spec='" + getSpec() + '\'' +
                ", config=" + getConfig() +
                '}';
    }
}
