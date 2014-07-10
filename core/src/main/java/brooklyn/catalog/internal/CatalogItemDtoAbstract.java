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
package brooklyn.catalog.internal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.BrooklynClassLoadingContextSequential;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.management.classloading.OsgiBrooklynClassLoadingContext;

public abstract class CatalogItemDtoAbstract<T,SpecT> implements CatalogItem<T,SpecT> {

    // TODO are ID and registeredType the same?
    String id;
    String registeredType;
    
    String javaType;
    String name;
    String description;
    String iconUrl;
    String version;
    CatalogLibrariesDto libraries;
    
    String planYaml;
    
    /** @deprecated since 0.7.0.
     * used for backwards compatibility when deserializing.
     * when catalogs are converted to new yaml format, this can be removed. */
    @Deprecated
    String type;
    
    public String getId() {
        if (id!=null) return id;
        return getRegisteredTypeName();
    }
    
    @Override
    public String getRegisteredTypeName() {
        if (registeredType!=null) return registeredType;
        return getJavaType();
    }
    
    public String getJavaType() {
        if (javaType!=null) return javaType;
        if (type!=null) return type;
        return null;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }

    public String getVersion() {
        return version;
    }

    @Nonnull
    @Override
    public CatalogItemLibraries getLibraries() {
        return getLibrariesDto();
    }

    public CatalogLibrariesDto getLibrariesDto() {
        return libraries;
    }

    @Nullable @Override
    public String getPlanYaml() {
        return planYaml;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+getId()+"/"+getName()+"]";
    }

    transient CatalogXmlSerializer serializer;
    
    public String toXmlString() {
        if (serializer==null) loadSerializer();
        return serializer.toString(this);
    }
    
    private synchronized void loadSerializer() {
        if (serializer==null) 
            serializer = new CatalogXmlSerializer();
    }

    public BrooklynClassLoadingContext newClassLoadingContext(final ManagementContext mgmt) {
        BrooklynClassLoadingContextSequential result = new BrooklynClassLoadingContextSequential(mgmt);
        
        if (getLibraries()!=null && getLibraries().getBundles()!=null && !getLibraries().getBundles().isEmpty())
            // TODO getLibraries() should never be null but sometimes it is still
            // e.g. run CatalogResourceTest without the above check
            result.add(new OsgiBrooklynClassLoadingContext(mgmt, getLibraries().getBundles()));

        BrooklynClassLoadingContext next = BrooklynLoaderTracker.getLoader();
        if (next==null) next = JavaBrooklynClassLoadingContext.newDefault(mgmt);
        result.add(next);
        
        return result;
    }

    public abstract Class<SpecT> getSpecType();
    
}
