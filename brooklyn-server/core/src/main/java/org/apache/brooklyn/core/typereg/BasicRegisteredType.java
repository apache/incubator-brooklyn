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
package org.apache.brooklyn.core.typereg;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.javalang.JavaClassNames;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableSet;

/** Instances are usually created by methods in {@link RegisteredTypes}. */
public class BasicRegisteredType implements RegisteredType {

    final RegisteredTypeKind kind;
    final String symbolicName;
    final String version;
    
    final List<OsgiBundleWithUrl> bundles = MutableList.of();
    String displayName;
    String description;
    String iconUrl;
    
    final Set<Object> superTypes = MutableSet.of();
    boolean deprecated;
    boolean disabled;
    final Set<String> aliases = MutableSet.of();
    final Set<Object> tags = MutableSet.of();
    
    TypeImplementationPlan implementationPlan;

    private transient ConfigBag cache = new ConfigBag();
    
    BasicRegisteredType(RegisteredTypeKind kind, String symbolicName, String version, TypeImplementationPlan implementationPlan) {
        this.kind = kind;
        this.symbolicName = symbolicName;
        this.version = version;
        this.implementationPlan = implementationPlan;
    }

    @Override
    public String getId() {
        if (symbolicName==null) return null;
        return symbolicName + (version!=null ? ":"+version : "");
    }
    
    @Override
    public RegisteredTypeKind getKind() {
        return kind;
    }

    @Override
    public String getSymbolicName() {
        return symbolicName;
    }

    @Override
    public String getVersion() {
        return version;
    }
    
    @Override
    public Collection<OsgiBundleWithUrl> getLibraries() {
        return ImmutableSet.copyOf(bundles);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }
    
    @Override
    public Set<Object> getSuperTypes() {
        return ImmutableSet.copyOf(superTypes);
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }
    
    @Override
    public boolean isDeprecated() {
        return deprecated;
    }
    
    @Override
    public Set<String> getAliases() {
        return ImmutableSet.copyOf(aliases);
    }

    @Override
    public Set<Object> getTags() {
        return ImmutableSet.copyOf(tags);
    }

    
    @Beta  // TODO depending how useful this is, it might be better to replace by a static WeakHashMap in RegisteredTypes
    public ConfigBag getCache() {
        return cache;
    }
    
    @Override
    public TypeImplementationPlan getPlan() {
        return implementationPlan;
    }
    
    @Override
    public String toString() {
        return JavaClassNames.simpleClassName(this)+"["+getId()+
            (isDisabled() ? ";DISABLED" : "")+
            (isDeprecated() ? ";deprecated" : "")+
            (getPlan()!=null ? ";"+getPlan().getPlanFormat() : "")+
            "]";
    }
}