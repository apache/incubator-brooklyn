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
package brooklyn.basic;

import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.util.text.Strings;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class BrooklynTypeSnapshot implements BrooklynType {
    private static final long serialVersionUID = 4670930188951106009L;
    
    private final String name;
    private transient volatile String simpleName;
    private final Map<String, ConfigKey<?>> configKeys;
    private final Set<ConfigKey<?>> configKeysSet;

    protected BrooklynTypeSnapshot(String name, Map<String, ConfigKey<?>> configKeys) {
        this.name = name;
        this.configKeys = ImmutableMap.copyOf(configKeys);
        this.configKeysSet = ImmutableSet.copyOf(this.configKeys.values());
    }

    @Override
    public String getName() {
        return name;
    }
    
    private String toSimpleName(String name) {
        String simpleName = name.substring(name.lastIndexOf(".")+1);
        if (Strings.isBlank(simpleName)) simpleName = name.trim();
        return Strings.makeValidFilename(simpleName);
    }

    @Override
    public String getSimpleName() {
        String sn = simpleName;
        if (sn==null) {
            sn = toSimpleName(getName());
            simpleName = sn;
        }
        return sn;
    }
    
    @Override
    public Set<ConfigKey<?>> getConfigKeys() {
        return configKeysSet;
    }
    
    @Override
    public ConfigKey<?> getConfigKey(String name) {
        return configKeys.get(name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(name, configKeys);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BrooklynTypeSnapshot)) return false;
        BrooklynTypeSnapshot o = (BrooklynTypeSnapshot) obj;
        
        return Objects.equal(name, o.name) && Objects.equal(configKeys, o.configKeys);
    }
    
    @Override
    public String toString() {
        return toStringHelper().toString();
    }
    
    protected ToStringHelper toStringHelper() {
        return Objects.toStringHelper(name)
                .add("configKeys", configKeys);
    }
}
