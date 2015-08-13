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

import com.google.common.base.Predicate;

public interface BrooklynCatalog {
    static String DEFAULT_VERSION = "0.0.0_DEFAULT_VERSION";

    /** @return The item with the given {@link brooklyn.catalog.CatalogItem#getSymbolicName()
     * symbolicName}, or null if not found.
     * @deprecated since 0.7.0 use {@link #getCatalogItem(String, String)};
     * or see also CatalogUtils getCatalogItemOptionalVersion */
    @Deprecated
    CatalogItem<?,?> getCatalogItem(String symbolicName);

    /** @return The item with the given {@link brooklyn.catalog.CatalogItem#getSymbolicName()
     * symbolicName}, or null if not found. */
    CatalogItem<?,?> getCatalogItem(String symbolicName, String version);

    /** @return Deletes the item with the given
     *  {@link brooklyn.catalog.CatalogItem#getSymbolicName() symbolicName}
     * @throws NoSuchElementException if not found
     * @deprecated since 0.7.0 use {@link #deleteCatalogItem(String, String)} */
    @Deprecated
    void deleteCatalogItem(String symbolicName);

    /** @return Deletes the item with the given {@link brooklyn.catalog.CatalogItem#getSymbolicName()
     * symbolicName} and version
     * @throws NoSuchElementException if not found */
    void deleteCatalogItem(String symbolicName, String version);

    /** variant of {@link #getCatalogItem(String, String)} which checks (and casts) type for convenience
     * (returns null if type does not match)
     * @deprecated since 0.7.0 use {@link #getCatalogItem(Class<T>, String, String)} */
    @Deprecated
    <T,SpecT> CatalogItem<T,SpecT> getCatalogItem(Class<T> type, String symbolicName);

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
    <T,SpecT> SpecT createSpec(CatalogItem<T,SpecT> item);
    
    /** throws exceptions if any problems 
     * @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
    @Deprecated
    <T,SpecT> Class<? extends T> loadClass(CatalogItem<T,SpecT> item);
    /** @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
    @Deprecated
    <T> Class<? extends T> loadClassByType(String typeName, Class<T> typeClass);
    /** @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
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
     * @deprecated since 0.7.0 Construct catalogs with OSGi bundles instead
     */
    @Deprecated
    CatalogItem<?,?> addItem(Class<?> clazz);

    void reset(Collection<CatalogItem<?, ?>> entries);

}
