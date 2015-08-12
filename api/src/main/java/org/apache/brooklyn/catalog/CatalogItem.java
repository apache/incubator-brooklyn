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
package org.apache.brooklyn.catalog;

import java.util.Collection;

import javax.annotation.Nullable;

import org.apache.brooklyn.mementos.CatalogItemMemento;

import brooklyn.basic.BrooklynObject;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;

import com.google.common.annotations.Beta;

@Beta
public interface CatalogItem<T,SpecT> extends BrooklynObject, Rebindable {
    
    public static enum CatalogItemType {
        TEMPLATE, 
        ENTITY, 
        POLICY,
        LOCATION;
    }
    
    public static interface CatalogBundle {
        public String getSymbolicName();
        public String getVersion();
        public String getUrl();

        /** @return true if the bundle reference contains both name and version*/
        public boolean isNamed();
    }

    @Deprecated
    public static interface CatalogItemLibraries {
        Collection<String> getBundles();
    }

    public CatalogItemType getCatalogItemType();

    /** @return The high-level type of this entity, e.g. Entity (not a specific Entity class) */
    public Class<T> getCatalogItemJavaType();

    /** @return The type of the spec e.g. EntitySpec corresponding to {@link #getCatalogItemJavaType()} */
    public Class<SpecT> getSpecType();
    
    /** @return The underlying java type of the item represented, or null if not known (e.g. if it comes from yaml) */
    @Nullable public String getJavaType();

    /** @deprecated since 0.7.0. Use {@link #getDisplayName} */
    @Deprecated
    public String getName();

    /** @deprecated since 0.7.0. Use {@link #getSymbolicName} */
    @Deprecated
    public String getRegisteredTypeName();

    @Nullable public String getDescription();

    @Nullable public String getIconUrl();

    public String getSymbolicName();

    public String getVersion();

    public Collection<CatalogBundle> getLibraries();

    public String toXmlString();

    /** @return The underlying YAML for this item, if known; 
     * currently including `services:` or `brooklyn.policies:` prefix (but this will likely be removed) */
    @Nullable public String getPlanYaml();

    @Override
    RebindSupport<CatalogItemMemento> getRebindSupport();
    
    /** Built up from {@link #getSymbolicName()} and {@link #getVersion()}.
     * 
     * (It is a bit self-referential having this method on this type of {@link BrooklynObject},
     * but it is easier this than making the interface hierarchy more complicated.) */
    @Override
    public String getCatalogItemId();

    public void setDeprecated(boolean deprecated);

    /**
     * @return True if the item has been deprecated and should not be shown in the catalog
     */
    boolean isDeprecated();
}
