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
package org.apache.brooklyn.api.catalog;

import java.util.Collection;
import java.util.NoSuchElementException;

import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;

public interface BrooklynCatalog {
    /** 
     * Version set in catalog when creator does not supply a version, to mean a low priority item;
     * and used when requesting to indicate the best version.
     * (See {@link #getCatalogItem(String, String)} for discussion of the best version.)
     */
    static String DEFAULT_VERSION = "0.0.0_DEFAULT_VERSION";

    /** @return The item matching the given given 
     * {@link CatalogItem#getSymbolicName() symbolicName} 
     * and optionally {@link CatalogItem#getVersion()},
     * taking the best version if the version is {@link #DEFAULT_VERSION} or null,
     * returning null if no matches are found. */
    CatalogItem<?,?> getCatalogItem(String symbolicName, String version);

    /** @return Deletes the item with the given {@link CatalogItem#getSymbolicName()
     * symbolicName} and version
     * @throws NoSuchElementException if not found */
    void deleteCatalogItem(String symbolicName, String version);

    /** variant of {@link #getCatalogItem(String, String)} which checks (and casts) type for convenience
     * (returns null if type does not match) */
    <T,SpecT> CatalogItem<T,SpecT> getCatalogItem(Class<T> type, String symbolicName, String version);

    /** @return All items in the catalog */
    <T,SpecT> Iterable<CatalogItem<T,SpecT>> getCatalogItems();

    /** convenience for filtering items in the catalog; see CatalogPredicates for useful filters */
    <T,SpecT> Iterable<CatalogItem<T,SpecT>> getCatalogItems(Predicate<? super CatalogItem<T,SpecT>> filter);

    /** persists the catalog item to the object store, if persistence is enabled */
    public void persist(CatalogItem<?, ?> catalogItem);

    /** @return The classloader which should be used to load classes and entities;
     * this includes all the catalog's classloaders in the right order.
     * This is a wrapper which will update as the underlying catalog items change,
     * so it is safe for callers to keep a handle on this. */
    public ClassLoader getRootClassLoader();

    /** creates a spec for the given catalog item, throwing exceptions if any problems */
    // TODO this should be cached on the item and renamed getSpec(...), else we re-create it too often (every time catalog is listed)
    <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createSpec(CatalogItem<T, SpecT> item);
    
    /** @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
    // used in one place in BRRU with warnings added in 0.9.0. remove after 0.9. 
    CatalogItem<?,?> getCatalogItemForType(String typeName);

    /**
     * Adds an item (represented in yaml) to the catalog.
     * Fails if the same version exists in catalog.
     *
     * @throws IllegalArgumentException if the yaml was invalid
     * @deprecated since 0.7.0 use {@link #addItems(String, boolean)}
     */
    @Deprecated
    CatalogItem<?,?> addItem(String yaml);
    
    /**
     * Adds an item (represented in yaml) to the catalog.
     * 
     * @param forceUpdate If true allows catalog update even when an
     * item exists with the same symbolicName and version
     *
     * @throws IllegalArgumentException if the yaml was invalid
     * @deprecated since 0.7.0 use {@link #addItems(String, boolean)}
     */
    @Deprecated
    CatalogItem<?,?> addItem(String yaml, boolean forceUpdate);
    
    /**
     * Adds items (represented in yaml) to the catalog.
     * Fails if the same version exists in catalog.
     *
     * @throws IllegalArgumentException if the yaml was invalid
     */
    Iterable<? extends CatalogItem<?,?>> addItems(String yaml);
    
    /**
     * Adds items (represented in yaml) to the catalog.
     * 
     * @param forceUpdate If true allows catalog update even when an
     * item exists with the same symbolicName and version
     *
     * @throws IllegalArgumentException if the yaml was invalid
     */
    Iterable<? extends CatalogItem<?,?>> addItems(String yaml, boolean forceUpdate);
    
    /**
     * adds an item to the 'manual' catalog;
     * this does not update the classpath or have a record to the java Class
     *
     * @deprecated since 0.7.0 Construct catalogs with yaml (referencing OSGi bundles) instead
     */
    // TODO maybe this should stay on the API? -AH Apr 2015 
    @Deprecated
    void addItem(CatalogItem<?,?> item);

    /**
     * Creates a catalog item and adds it to the 'manual' catalog,
     * with the corresponding Class definition (loaded by a classloader)
     * registered and available in the classloader.
     * <p>
     * Note that the class will be available for this session only,
     * although the record of the item will appear in the catalog DTO if exported,
     * so it is recommended to edit the 'manual' catalog DTO if using it to
     * generate a catalog, either adding the appropriate classpath URL or removing this entry.
     *
     * @deprecated since 0.7.0 Construct catalogs with OSGi bundles instead.
     * This is used in a handful of tests which should be rewritten to refer to OSGi bundles.
     */
    @Deprecated
    @VisibleForTesting
    CatalogItem<?,?> addItem(Class<?> clazz);

    void reset(Collection<CatalogItem<?, ?>> entries);

}
