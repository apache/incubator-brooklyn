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

import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.util.javalang.JavaClassNames;

public class BasicRegisteredType implements RegisteredType {

    final String symbolicName;
    final String version;
    final RegisteredTypeKind kind;
    
    List<OsgiBundleWithUrl> bundles;
    String displayName;
    String description;
    String iconUrl;
    boolean deprecated;
    boolean disabled;
    
    TypeImplementationPlan implementationPlan;

    // TODO ensure this is re-populated on rebind?  or remove?
    transient Class<?> javaType;
    
    public BasicRegisteredType(RegisteredTypeKind kind, String symbolicName, String version, Class<?> javaType, TypeImplementationPlan implementationPlan) {
        this.kind = kind;
        this.symbolicName = symbolicName;
        this.version = version;
        this.javaType = javaType;
        this.implementationPlan = implementationPlan;
    }

    @Override
    public String getId() {
        return symbolicName + (version!=null ? ":"+version : "");
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
    public RegisteredTypeKind getKind() {
        return kind;
    }
    
    @Override
    public Collection<OsgiBundleWithUrl> getLibraries() {
        return bundles;
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
    public boolean isDisabled() {
        return disabled;
    }
    
    @Override
    public boolean isDeprecated() {
        return deprecated;
    }
    
    @Override
    public Class<?> getJavaType() {
        return javaType;
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
            "]";
    }
}