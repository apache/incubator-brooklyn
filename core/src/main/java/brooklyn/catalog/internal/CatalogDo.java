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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.management.ManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.AggregateClassLoader;
import brooklyn.util.net.Urls;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class CatalogDo {

    private static final Logger log = LoggerFactory.getLogger(CatalogDo.class);
    
    volatile boolean isLoaded = false;
    final CatalogDto dto;
    ManagementContext mgmt = null;
    CatalogDo parent = null;
    
    List<CatalogDo> childrenCatalogs = new ArrayList<CatalogDo>();
    CatalogClasspathDo classpath;
    private Map<String, CatalogItemDo<?,?>> cacheById;

    AggregateClassLoader childrenClassLoader = AggregateClassLoader.newInstanceWithNoLoaders();
    ClassLoader recursiveClassLoader;

    protected CatalogDo(CatalogDto dto) {
        this.dto = Preconditions.checkNotNull(dto);
    }
    
    public CatalogDo(ManagementContext mgmt, CatalogDto dto) {
        this(dto);
        this.mgmt = mgmt;
    }

    boolean isLoaded() {
        return isLoaded;
    }

    /** Calls {@link #load(CatalogDo)} with a null parent. */
    public CatalogDo load() {
        return load(null);
    }

    /** Calls {@link #load(ManagementContext, CatalogDo)} with the catalog's existing management context. */
    public CatalogDo load(CatalogDo parent) {
        return load(mgmt, parent);
    }

    /** Calls {@link #load(ManagementContext, CatalogDo, boolean)} failing on load errors. */
    public synchronized CatalogDo load(ManagementContext mgmt, CatalogDo parent) {
        return load(mgmt, parent, true);
    }

    /** causes all URL-based catalogs to have their manifests loaded,
     * and all scanning-based classpaths to scan the classpaths
     * (but does not load all JARs)
     */
    public synchronized CatalogDo load(ManagementContext mgmt, CatalogDo parent, boolean failOnLoadError) {
        if (isLoaded()) {
            if (mgmt!=null && !Objects.equal(mgmt, this.mgmt)) {
                throw new IllegalStateException("Cannot set mgmt "+mgmt+" on "+this+" after catalog is loaded");
            }
            log.debug("Catalog "+this+" is already loaded");
            return this;
        }
        loadThisCatalog(mgmt, parent, failOnLoadError);
        loadChildrenCatalogs(failOnLoadError);
        buildCaches();
        return this;
    }

    protected synchronized void loadThisCatalog(ManagementContext mgmt, CatalogDo parent, boolean failOnLoadError) {
        if (isLoaded()) return;
        CatalogUtils.logDebugOrTraceIfRebinding(log, "Loading catalog {} into {}", this, parent);
        if (this.parent!=null && !this.parent.equals(parent))
            log.warn("Catalog "+this+" being initialised with different parent "+parent+" when already parented by "+this.parent, new Throwable("source of reparented "+this));
        if (this.mgmt!=null && !this.mgmt.equals(mgmt))
            log.warn("Catalog "+this+" being initialised with different mgmt "+mgmt+" when already managed by "+this.mgmt, new Throwable("source of reparented "+this));
        this.parent = parent;
        this.mgmt = mgmt;
        dto.populate();
        loadCatalogClasspath();
        loadCatalogItems(failOnLoadError);
        isLoaded = true;
        synchronized (this) {
            notifyAll();
        }
    }

    private void loadCatalogClasspath() {
        try {
            classpath = new CatalogClasspathDo(this);
            classpath.load();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.error("Unable to load catalog "+this+" (ignoring): "+e);
            log.info("Trace for failure to load "+this+": "+e, e);
        }
    }

    private void loadCatalogItems(boolean failOnLoadError) {
        Iterable<CatalogItemDtoAbstract<?, ?>> entries = dto.getUniqueEntries();
        if (entries!=null) {
            for (CatalogItemDtoAbstract<?,?> entry : entries) {
                try {
                    CatalogUtils.installLibraries(mgmt, entry.getLibraries());
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    if (failOnLoadError) {
                        Exceptions.propagate(e);
                    } else {
                        log.error("Loading bundles for catalog item " + entry + " failed: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    public boolean blockIfNotLoaded(Duration timeout) throws InterruptedException {
        if (isLoaded()) return true;
        synchronized (this) {
            if (isLoaded()) return true;
            CountdownTimer timer = CountdownTimer.newInstanceStarted(timeout);
            while (!isLoaded())
                if (!timer.waitOnForExpiry(this))
                    return false;
            return true;
        }
    }
    
    protected void loadChildrenCatalogs(boolean failOnLoadError) {
        if (dto.catalogs!=null) {
            for (CatalogDto child: dto.catalogs) {
                loadCatalog(child, failOnLoadError);
            }
        }
    }
    
    CatalogDo loadCatalog(CatalogDto child, boolean failOnLoadError) {
        CatalogDo childL = new CatalogDo(child);
        childrenCatalogs.add(childL);
        childL.load(mgmt, this, failOnLoadError);
        childrenClassLoader.addFirst(childL.getRecursiveClassLoader());
        clearCache(false);
        return childL;
    }

    protected Map<String, CatalogItemDo<?,?>> getIdCache() {
        Map<String, CatalogItemDo<?,?>> cache = this.cacheById;
        if (cache==null) cache = buildCaches();
        return cache;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected synchronized Map<String, CatalogItemDo<?,?>> buildCaches() {
        if (cacheById != null) return cacheById;
        CatalogUtils.logDebugOrTraceIfRebinding(log, "Building cache for {}", this);
        if (!isLoaded()) 
            log.debug("Catalog not fully loaded when loading cache of "+this);
        
        Map<String, CatalogItemDo<?,?>> cache = new LinkedHashMap<String, CatalogItemDo<?,?>>();
        
        // build the cache; first from children catalogs, then from local entities
        // so that root and near-root takes precedence over deeper items;
        // and go through in reverse order so that things at the top of the file take precedence
        // (both in the cache and in the aggregate class loader);
        // however anything added _subsequently_ will take precedence (again in both)
        if (dto.catalogs!=null) { 
            List<CatalogDo> catalogsReversed = new ArrayList<CatalogDo>(childrenCatalogs);
            Collections.reverse(catalogsReversed);
            for (CatalogDo child: catalogsReversed) {
                cache.putAll(child.getIdCache());
            }
        }
        if (dto.getUniqueEntries()!=null) {
            List<CatalogItemDtoAbstract<?,?>> entriesReversed = MutableList.copyOf(dto.getUniqueEntries());
            Collections.reverse(entriesReversed);
            for (CatalogItemDtoAbstract<?,?> entry: entriesReversed)
                cache.put(entry.getId(), new CatalogItemDo(this, entry));
        }
        this.cacheById = cache;
        return cache;
    }
    
    protected synchronized void clearCache(boolean deep) {
        this.cacheById = null;
        if (deep) {
            for (CatalogDo child : childrenCatalogs) {
                child.clearCache(true);
            }
        }
        clearParentCache();
    }
    protected void clearParentCache() {
        if (this.parent!=null)
            this.parent.clearCache(false);
    }
    
    /**
     * Adds the given entry to the catalog, with no enrichment.
     * Callers may prefer {@link CatalogClasspathDo#addCatalogEntry(CatalogItemDtoAbstract, Class)}
     */
    public synchronized void addEntry(CatalogItemDtoAbstract<?,?> entry) {
        dto.addEntry(entry);
        
        // could do clearCache(false); but this is slightly more efficient...
        if (cacheById != null) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            CatalogItemDo<?, ?> cdo = new CatalogItemDo(this, entry);
            cacheById.put(entry.getId(), cdo);
        }        
        clearParentCache();
        
        if (mgmt != null) {
            mgmt.getRebindManager().getChangeListener().onManaged(entry);
        }
   }
    
    /**
     * Removes the given entry from the catalog.
     */
    public synchronized void deleteEntry(CatalogItemDtoAbstract<?, ?> entry) {
        dto.removeEntry(entry);
        
        // could do clearCache(false); but this is slightly more efficient...
        if (cacheById != null) {
            cacheById.remove(entry.getId());
        }
        clearParentCache();
        
        if (mgmt != null) {
            // TODO: Can the entry be in more than one catalogue? The management context has no notion of
            // catalogue hierarchy so this will effectively remove it from all catalogues.
            // (YES- we're assuming ID's are unique across all catalogues; if not, things get out of sync;
            // however see note at top of BasicBrooklynCatalog --
            // manualCatalog and OSGi is used for everything now except legacy XML trees)
            mgmt.getRebindManager().getChangeListener().onUnmanaged(entry);
        }
    }

    /** returns loaded catalog, if this has been loaded */
    CatalogDo addCatalog(CatalogDto child) {
        if (dto.catalogs == null)
            dto.catalogs = new ArrayList<CatalogDto>();
        dto.catalogs.add(child);
        if (!isLoaded())
            return null;
        return loadCatalog(child, true);
    }
    
    /** adds the given urls; filters out any nulls supplied */
    public synchronized void addToClasspath(String ...urls) {
        if (dto.classpath == null)
            dto.classpath = new CatalogClasspathDto();
        for (String url: urls) {
            if (url!=null)
                dto.classpath.addEntry(url);
        }
        if (isLoaded())
            throw new IllegalStateException("dynamic classpath entry value update not supported");
        // easy enough to add, just support unload+reload (and can also allow dynamic setScan below)
        // but more predictable if we don't; the one exception is in the manualAdditionsCatalog
        // where BasicBrooklynCatalog reaches in and updates the DTO and/or CompositeClassLoader directly, if necessary
//            for (String url: urls)
//                loadedClasspath.addEntry(url);
    }

    public synchronized void setClasspathScanForEntities(CatalogScanningModes value) {
        if (dto.classpath == null)
            dto.classpath = new CatalogClasspathDto();
        dto.classpath.scan = value;
        if (isLoaded()) 
            throw new IllegalStateException("dynamic classpath scan value update not supported");
        // easy enough to add, see above
    }

    @Override
    public String toString() {
        String size = cacheById == null ? "not yet loaded" : "size " + cacheById.size();
        return "Loaded:" + dto + "(" + size + ")";
    }

    /** is "local" if it and all ancestors are not based on any remote urls */ 
    public boolean isLocal() {
        if (dto.url != null) {
            String proto = Urls.getProtocol(dto.url);
            if (proto != null) {
                // 'file' is the only protocol accepted as "local"
                if (!"file".equals(proto)) return false;
            }
        }
        return parent == null || parent.isLocal();
    }

    /** classloader for only the entries in this catalog's classpath */ 
    public ClassLoader getLocalClassLoader() {
        if (classpath != null) return classpath.getLocalClassLoader();
        return null;
    }

    /** recursive classloader is the local classloader plus all children catalog's classloader */
    public ClassLoader getRecursiveClassLoader() {
        if (recursiveClassLoader == null) loadRecursiveClassLoader();
        return recursiveClassLoader;
    }
    
    protected synchronized void loadRecursiveClassLoader() {
        if (recursiveClassLoader!=null) return;
        AggregateClassLoader cl = AggregateClassLoader.newInstanceWithNoLoaders();
        cl.addFirst(childrenClassLoader);
        ClassLoader local = getLocalClassLoader();
        if (local != null) cl.addFirst(local);
        if (parent == null) {
            // we are root.  include the mgmt base classloader and/or standard class loaders 
            ClassLoader base = mgmt != null ? ((ManagementContextInternal)mgmt).getBaseClassLoader() : null;
            if (base != null) cl.addFirst(base);
            else {
                cl.addFirst(getClass().getClassLoader());
                cl.addFirst(Object.class.getClassLoader());
            }
        }
        recursiveClassLoader = cl;
    }
    
    /** the root classloader is the recursive CL from the outermost catalog
     * (which includes the base classloader from the mgmt context, if set) */
    public ClassLoader getRootClassLoader() {
        if (parent != null) return parent.getRootClassLoader();
        return getRecursiveClassLoader();
    }

}
