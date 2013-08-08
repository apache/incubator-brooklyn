package brooklyn.catalog.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.AggregateClassLoader;

import com.google.common.base.Preconditions;

public class CatalogDo {

    private static final Logger log = LoggerFactory.getLogger(CatalogDo.class);
    
    volatile boolean isLoaded = false;
    final CatalogDto dto;
    ManagementContext mgmt = null;
    CatalogDo parent = null;
    
    List<CatalogDo> childrenCatalogs = new ArrayList<CatalogDo>();
    CatalogClasspathDo classpath;
    Map<String, CatalogItemDo<?>> cache;
    
    AggregateClassLoader childrenClassLoader = AggregateClassLoader.newInstanceWithNoLoaders();
    ClassLoader recursiveClassLoader;

    public CatalogDo(CatalogDto dto) {
        this.dto = Preconditions.checkNotNull(dto);
    }
    
    boolean isLoaded() {
        return isLoaded;
    }

    /** causes all URL-based catalogs to have their manifests loaded,
     * and all scanning-based classpaths to scan the classpaths
     * (but does not load all JARs)
     */
    public synchronized CatalogDo load(ManagementContext mgmt, CatalogDo parent) {
        if (isLoaded()) return this;
        loadThisCatalog(mgmt, parent);
        loadChildrenCatalogs();
        getCache();
        return this;
    }
    
    protected synchronized void loadThisCatalog(ManagementContext mgmt, CatalogDo parent) {
        if (isLoaded()) return;
        this.parent = parent;
        this.mgmt = mgmt;
        try {
            if (dto.url!=null)
                CatalogDtoUtils.populateFromUrl(dto, dto.url);
            classpath = new CatalogClasspathDo(this);
            classpath.load();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.error("Unable to load catalog "+this+" (ignoring): "+e);
//            log.debug("Trace for failure to load "+this+": "+e, e);
            log.info("Trace for failure to load "+this+": "+e, e);
        }
        isLoaded = true;
    }
    
    protected void loadChildrenCatalogs() {
        if (dto.catalogs!=null) {
            for (CatalogDto child: dto.catalogs) {
                loadCatalog(child);
            }
        }
    }
    
    CatalogDo loadCatalog(CatalogDto child) {
        CatalogDo childL = new CatalogDo(child);
        childrenCatalogs.add(childL);
        childL.load(mgmt, this);
        childrenClassLoader.addFirst(childL.getRecursiveClassLoader());
        clearCache(false);
        return childL;
    }

    protected Map<String, CatalogItemDo<?>> getCache() {
        Map<String, CatalogItemDo<?>> cache = this.cache;
        if (cache==null) cache = buildCache();
        return cache;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected synchronized Map<String, CatalogItemDo<?>> buildCache() {
        if (cache!=null) return cache;
        log.debug("Building cache for "+this);
        if (!isLoaded()) 
            log.debug("Catalog not fully loaded when loading cache of "+this);
        
        Map<String, CatalogItemDo<?>> cache = new LinkedHashMap<String, CatalogItemDo<?>>();
        
        // build the cache; first from children catalogs, then from local entities
        // so that root and near-root takes precedence over deeper items;
        // and go through in reverse order so that things at the top of the file take precedence
        // (both in the cache and in the aggregate class loader);
        // however anything added _subsequently_ will take precedence (again in both)
        if (dto.catalogs!=null) { 
            List<CatalogDo> catalogsReversed = new ArrayList<CatalogDo>(childrenCatalogs);
            Collections.reverse(catalogsReversed);
            for (CatalogDo child: catalogsReversed)
                cache.putAll(child.getCache());
        }
        if (dto.entries!=null) {
            List<CatalogItemDtoAbstract<?>> entriesReversed = new ArrayList<CatalogItemDtoAbstract<?>>(dto.entries);
            Collections.reverse(entriesReversed);
            for (CatalogItemDtoAbstract<?> entry: entriesReversed)
                cache.put(entry.getId(), new CatalogItemDo(this, entry));
        }
        
        this.cache = cache;
        return cache;
    }
    
    protected synchronized void clearCache(boolean deep) {
        this.cache = null;
        if (deep) 
            for (CatalogDo child: childrenCatalogs) child.clearCache(true); 
    }
    
    /** adds the given entry to the catalog, with no enrichment;
     * callers may prefer {@link CatalogClasspathDo#addCatalogEntry(CatalogItemDtoAbstract, Class)}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public synchronized void addEntry(CatalogItemDtoAbstract<?> entry) {
        if (dto.entries==null) 
            dto.entries = new ArrayList<CatalogItemDtoAbstract<?>>();
        dto.entries.add(entry);
        if (cache!=null)
            cache.put(entry.getId(), new CatalogItemDo(this, entry));
    }

    /** returns loaded catalog, if this has been loaded */
    CatalogDo addCatalog(CatalogDto child) {
        if (dto.catalogs==null) 
            dto.catalogs = new ArrayList<CatalogDto>();
        dto.catalogs.add(child);
        if (!isLoaded())
            return null;
        return loadCatalog(child);
    }
    
    public synchronized void addToClasspath(String ...urls) {
        if (dto.classpath==null)
            dto.classpath = new CatalogClasspathDto();
        for (String url: urls)
            dto.classpath.addEntry(url);
        if (isLoaded())
            throw new IllegalStateException("dynamic classpath entry value update not supported");
        // easy enough to add, just support unload+reload (and can also allow dynamic setScan below)
        // but more predictable if we don't; the one exception is in the manualAdditionsCatalog
        // where BasicBrooklynCatalog reaches in and updates the DTO and/or CompositeClassLoader directly, if necessary
//            for (String url: urls)
//                loadedClasspath.addEntry(url);
    }

    public synchronized void setClasspathScanForEntities(CatalogScanningModes value) {
        if (dto.classpath==null)
            dto.classpath = new CatalogClasspathDto();
        dto.classpath.scan = value;
        if (isLoaded()) 
            throw new IllegalStateException("dynamic classpath scan value update not supported");
        // easy enough to add, see above
    }

    @Override
    public String toString() {
        return "Loaded:"+dto+"("+
                (cache==null ? "not yet loaded" : "size "+cache.size())+
                ")";
    }

    /** is "local" if it and all ancestors are not based on any remote urls */ 
    public boolean isLocal() {
        if (dto.url!=null) {
            String proto = ResourceUtils.getProtocol(dto.url);
            if (proto!=null) {
                // 'file' is the only protocol accepted as "local"
                if (!"file".equals(proto)) return false;
            }
        }
        if (parent==null) return true;
        return parent.isLocal();
    }

    /** classloader for only the entries in this catalog's classpath */ 
    public ClassLoader getLocalClassLoader() {
        if (classpath!=null) return classpath.getLocalClassLoader();
        return null;
    }

    /** recursive classloader is the local classloader plus all children catalog's classloader */
    public ClassLoader getRecursiveClassLoader() {
        if (recursiveClassLoader==null) loadRecursiveClassLoader();
        return recursiveClassLoader;
    }
    
    protected synchronized void loadRecursiveClassLoader() {
        if (recursiveClassLoader!=null) return;
        AggregateClassLoader cl = AggregateClassLoader.newInstanceWithNoLoaders();
        cl.addFirst(childrenClassLoader);
        ClassLoader local = getLocalClassLoader();
        if (local!=null) cl.addFirst(local);
        if (parent==null) {
            // we are root.  include the mgmt base classloader and/or standard class loaders 
            ClassLoader base = ((ManagementContextInternal)mgmt).getBaseClassLoader();
            if (base!=null) cl.addFirst(base);
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
        if (parent!=null) return parent.getRootClassLoader();
        return getRecursiveClassLoader();
    }

}
