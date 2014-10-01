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
package brooklyn.catalog;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import brooklyn.basic.BrooklynObject;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.mementos.CatalogItemMemento;

import com.google.common.annotations.Beta;

@Beta
public interface CatalogItem<T,SpecT> extends BrooklynObject, Rebindable {
    
    public static enum CatalogItemType {
        TEMPLATE, ENTITY, POLICY, CONFIGURATION
    }

    @Beta
    public static interface CatalogItemLibraries {
        List<String> getBundles();
    }

    public CatalogItemType getCatalogItemType();

    /** @return The high-level type of this entity, e.g. Entity (not a specific Entity class) */
    public Class<T> getCatalogItemJavaType();

    /** @return The type of the spec e.g. EntitySpec corresponding to {@link #getCatalogItemJavaType()} */
    public Class<SpecT> getSpecType();
    
    /** @return The type name registered in the catalog for this item */
    @Nonnull
    public String getRegisteredTypeName();
    
    /** @return The underlying java type of the item represented, or null if not known (e.g. if it comes from yaml) */
    // TODO references to this should probably query getRegisteredType
    @Nullable public String getJavaType();

    /** @deprecated since 0.7.0. Use {@link #getDisplayName} */
    @Deprecated
    public String getName();

    public String getDescription();

    public String getIconUrl();

    public String getVersion();

    public CatalogItemLibraries getLibraries();

    public String toXmlString();

    /** @return The underlying YAML for this item, if known */
    @Nullable public String getPlanYaml();

    @Override
    RebindSupport<CatalogItemMemento> getRebindSupport();
}

