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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.internal.EntityManagementUtils;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.AggregateClassLoader;
import brooklyn.util.javalang.LoadedClassLoader;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

public class BasicBrooklynCatalog implements BrooklynCatalog {
    public static final String NO_VERSION = "0.0.0.SNAPSHOT";

    private static final Logger log = LoggerFactory.getLogger(BasicBrooklynCatalog.class);

    public static class BrooklynLoaderTracker {
        public static final ThreadLocal<BrooklynClassLoadingContext> loader = new ThreadLocal<BrooklynClassLoadingContext>();

        public static BrooklynClassLoadingContext setLoader(BrooklynClassLoadingContext val) {
            BrooklynClassLoadingContext prev = loader.get();
            loader.set(val);
            return prev;
        }

        // TODO Stack, for recursive calls?
        public static void unsetLoader(BrooklynClassLoadingContext val) {
            loader.set(null);
        }

        public static BrooklynClassLoadingContext getLoader() {
            return loader.get();
        }
    }

    private final ManagementContext mgmt;
    private CatalogDo catalog;
    private volatile CatalogDo manualAdditionsCatalog;
    private volatile LoadedClassLoader manualAdditionsClasses;

    public BasicBrooklynCatalog(ManagementContext mgmt) {
        this(mgmt, CatalogDto.newNamedInstance("empty catalog", "empty catalog", "empty catalog, expected to be reset later"));
    }

    public BasicBrooklynCatalog(ManagementContext mgmt, CatalogDto dto) {
        this.mgmt = checkNotNull(mgmt, "managementContext");
        this.catalog = new CatalogDo(mgmt, dto);
    }

    public boolean blockIfNotLoaded(Duration timeout) {
        try {
            return getCatalog().blockIfNotLoaded(timeout);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public void reset(CatalogDto dto) {
        // Unregister all existing persisted items.
        for (CatalogItem<?, ?> toRemove : getCatalogItems()) {
            if (log.isTraceEnabled()) {
                log.trace("Scheduling item for persistence removal: {}", toRemove.getId());
            }
            mgmt.getRebindManager().getChangeListener().onUnmanaged(toRemove);
        }
        CatalogDo catalog = new CatalogDo(mgmt, dto);
        CatalogUtils.logDebugOrTraceIfRebinding(log, "Resetting "+this+" catalog to "+dto);
        catalog.load(mgmt, null);
        CatalogUtils.logDebugOrTraceIfRebinding(log, "Reloaded catalog for "+this+", now switching");
        this.catalog = catalog;

        // Inject management context into and persist all the new entries.
        for (CatalogItem<?, ?> entry : getCatalogItems()) {
            boolean setManagementContext = false;
            if (entry instanceof CatalogItemDo) {
                CatalogItemDo<?, ?> cid = CatalogItemDo.class.cast(entry);
                if (cid.getDto() instanceof CatalogItemDtoAbstract) {
                    CatalogItemDtoAbstract<?, ?> cdto = CatalogItemDtoAbstract.class.cast(cid.getDto());
                    if (cdto.getManagementContext() == null) {
                        cdto.setManagementContext((ManagementContextInternal) mgmt);
                    }
                    setManagementContext = true;
                }
            }
            if (!setManagementContext) {
                log.warn("Can't set management context on entry with unexpected type in catalog. type={}, " +
                        "expected={}", entry, CatalogItemDo.class);
            }
            if (log.isTraceEnabled()) {
                log.trace("Scheduling item for persistence addition: {}", entry.getId());
            }
            mgmt.getRebindManager().getChangeListener().onManaged(entry);
        }

    }

    /**
     * Resets the catalog to the given entries
     */
    @Override
    public void reset(Collection<CatalogItem<?, ?>> entries) {
        CatalogDto newDto = CatalogDto.newDtoFromCatalogItems(entries, "explicit-catalog-reset");
        reset(newDto);
    }
    
    public CatalogDo getCatalog() {
        return catalog;
    }

    protected CatalogItemDo<?,?> getCatalogItemDo(String symbolicName, String version) {
        String fixedVersionId = getFixedVersionId(symbolicName, version);
        if (fixedVersionId == null) {
            //no items with symbolicName exist
            return null;
        }

        String versionedId = CatalogUtils.getVersionedId(symbolicName, fixedVersionId);
        CatalogItemDo<?, ?> item = null;
        //TODO should remove "manual additions" bucket; just have one map a la osgi
        if (manualAdditionsCatalog!=null) item = manualAdditionsCatalog.getIdCache().get(versionedId);
        if (item == null) item = catalog.getIdCache().get(versionedId);
        return item;
    }
    
    private String getFixedVersionId(String symbolicName, String version) {
        if (!DEFAULT_VERSION.equals(version)) {
            return version;
        } else {
            return getDefaultVersion(symbolicName);
        }
    }

    private String getDefaultVersion(String symbolicName) {
        Iterable<CatalogItem<Object, Object>> versions = getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName)));
        Collection<CatalogItem<Object, Object>> orderedVersions = sortVersionsDesc(versions);
        if (!orderedVersions.isEmpty()) {
            return orderedVersions.iterator().next().getVersion();
        } else {
            return null;
        }
    }

    private <T,SpecT> Collection<CatalogItem<T,SpecT>> sortVersionsDesc(Iterable<CatalogItem<T,SpecT>> versions) {
        return ImmutableSortedSet.orderedBy(CatalogItemComparator.<T,SpecT>getInstance()).addAll(versions).build();
    }

    @Override
    @Deprecated
    public CatalogItem<?,?> getCatalogItem(String symbolicName) {
        return getCatalogItem(symbolicName, DEFAULT_VERSION);
    }
    
    @Override
    public CatalogItem<?,?> getCatalogItem(String symbolicName, String version) {
        if (symbolicName == null) return null;
        checkNotNull(version, "version");
        CatalogItemDo<?, ?> itemDo = getCatalogItemDo(symbolicName, version);
        if (itemDo == null) return null;
        return itemDo.getDto();
    }
    
    @Override
    @Deprecated
    public void deleteCatalogItem(String id) {
        //Delete only if installed through the
        //deprecated methods. Don't support DEFAULT_VERSION for delete.
        deleteCatalogItem(id, NO_VERSION);
    }

    @Override
    public void deleteCatalogItem(String symbolicName, String version) {
        log.debug("Deleting manual catalog item from "+mgmt+": "+symbolicName + ":" + version);
        checkNotNull(symbolicName, "id");
        checkNotNull(version, "version");
        if (DEFAULT_VERSION.equals(version)) {
            throw new IllegalStateException("Deleting items with unspecified version (argument DEFAULT_VERSION) not supported.");
        }
        CatalogItem<?, ?> item = getCatalogItem(symbolicName, version);
        CatalogItemDtoAbstract<?,?> itemDto = getAbstractCatalogItem(item);
        if (itemDto == null) {
            throw new NoSuchElementException("No catalog item found with id "+symbolicName);
        }
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsCatalog.deleteEntry(itemDto);
        
        // Ensure the cache is de-populated
        getCatalog().deleteEntry(itemDto);

        // And indicate to the management context that it should be removed.
        if (log.isTraceEnabled()) {
            log.trace("Scheduling item for persistence removal: {}", itemDto.getId());
        }
        mgmt.getRebindManager().getChangeListener().onUnmanaged(itemDto);

    }

    @Override
    @Deprecated
    public <T,SpecT> CatalogItem<T,SpecT> getCatalogItem(Class<T> type, String id) {
        return getCatalogItem(type, id, DEFAULT_VERSION);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T,SpecT> CatalogItem<T,SpecT> getCatalogItem(Class<T> type, String id, String version) {
        if (id==null || version==null) return null;
        CatalogItem<?,?> result = getCatalogItem(id, version);
        if (result==null) return null;
        if (type==null || type.isAssignableFrom(result.getCatalogItemJavaType())) 
            return (CatalogItem<T,SpecT>)result;
        return null;
    }

    @Override
    public void persist(CatalogItem<?, ?> catalogItem) {
        checkArgument(getCatalogItem(catalogItem.getSymbolicName(), catalogItem.getVersion()) != null, "Unknown catalog item %s", catalogItem);
        mgmt.getRebindManager().getChangeListener().onChanged(catalogItem);
    }
    
    @Override
    public ClassLoader getRootClassLoader() {
        return catalog.getRootClassLoader();
    }

    /**
     * Loads this catalog. No effect if already loaded.
     */
    public void load() {
        log.debug("Loading catalog for " + mgmt);
        getCatalog().load(mgmt, null);
        if (log.isDebugEnabled()) {
            log.debug("Loaded catalog for " + mgmt + ": " + catalog + "; search classpath is " + catalog.getRootClassLoader());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, SpecT> SpecT createSpec(CatalogItem<T, SpecT> item) {
        CatalogItemDo<T,SpecT> loadedItem = (CatalogItemDo<T, SpecT>) getCatalogItemDo(item.getSymbolicName(), item.getVersion());
        if (loadedItem == null) return null;
        Class<SpecT> specType = loadedItem.getSpecType();
        if (specType==null) return null;

        String yaml = loadedItem.getPlanYaml();

        if (yaml!=null) {
            SpecT spec = (SpecT) EntityManagementUtils.createSpec(mgmt, yaml, CatalogUtils.newClassLoadingContext(mgmt, loadedItem));
            ((AbstractBrooklynObjectSpec<?, ?>)spec).catalogItemId(item.getId());
            return spec;
        }

        // revert to legacy mechanism
        SpecT spec = null;
        try {
            if (loadedItem.getJavaType()!=null) {
                SpecT specT = (SpecT) Reflections.findMethod(specType, "create", Class.class).invoke(null, loadedItem.loadJavaClass(mgmt));
                spec = specT;
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalStateException("Unsupported creation of spec type "+specType+"; it must have a public static create(Class) method", e);
        }

        if (spec==null) 
            throw new IllegalStateException("Unknown how to create instance of "+this);

        return spec;
    }

    @SuppressWarnings("unchecked")
    @Override
    /** @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
    @Deprecated
    public <T,SpecT> Class<? extends T> loadClass(CatalogItem<T,SpecT> item) {
        if (log.isDebugEnabled())
            log.debug("Loading class for catalog item " + item);
        checkNotNull(item);
        CatalogItemDo<?,?> loadedItem = getCatalogItemDo(item.getSymbolicName(), item.getVersion());
        if (loadedItem==null) throw new NoSuchElementException("Unable to load '"+item.getId()+"' to instantiate it");
        return (Class<? extends T>) loadedItem.getJavaClass();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    /** @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
    @Deprecated
    public <T> Class<? extends T> loadClassByType(String typeName, Class<T> typeClass) {
        final CatalogItem<?,?> resultI = getCatalogItemForType(typeName);

        if (resultI == null) {
            throw new NoSuchElementException("Unable to find catalog item for type "+typeName);
        }

        return (Class<? extends T>) loadClass(resultI);
    }

    @Deprecated /** @deprecated since 0.7.0 only used by other deprecated items */ 
    private <T,SpecT> CatalogItemDtoAbstract<T,SpecT> getAbstractCatalogItem(CatalogItem<T,SpecT> item) {
        while (item instanceof CatalogItemDo) item = ((CatalogItemDo<T,SpecT>)item).itemDto;
        if (item==null) return null;
        if (item instanceof CatalogItemDtoAbstract) return (CatalogItemDtoAbstract<T,SpecT>) item;
        throw new IllegalStateException("Cannot unwrap catalog item '"+item+"' (type "+item.getClass()+") to restore DTO");
    }

    @Override
    public CatalogItem<?,?> addItem(String yaml) {
        return addItem(yaml, false);
    }

    @Override
    public CatalogItem<?,?> addItem(String yaml, boolean forceUpdate) {
        log.debug("Adding manual catalog item to "+ mgmt + ": "+yaml);
        checkNotNull(yaml, "yaml");
        CatalogItemDtoAbstract<?,?> itemDto = EntityManagementUtils.createCatalogItem(mgmt, yaml);
        checkItemNotExists(itemDto, forceUpdate);

        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsCatalog.addEntry(itemDto);

        // Ensure the cache is populated and it is persisted by the management context
        getCatalog().addEntry(itemDto);

        // Request that the management context persist the item.
        if (log.isTraceEnabled()) {
            log.trace("Scheduling item for persistence addition: {}", itemDto.getId());
        }
        mgmt.getRebindManager().getChangeListener().onManaged(itemDto);

        return itemDto;
    }

    private void checkItemNotExists(CatalogItem<?,?> itemDto, boolean forceUpdate) {
        if (!forceUpdate && getCatalogItemDo(itemDto.getSymbolicName(), itemDto.getVersion()) != null) {
            throw new IllegalStateException("Updating existing catalog entries is forbidden: " +
                    itemDto.getSymbolicName() + ":" + itemDto.getVersion() + ". Use forceUpdate argument to override.");
        }
    }

    @Override @Deprecated /** @deprecated see super */
    public void addItem(CatalogItem<?,?> item) {
        //assume forceUpdate for backwards compatibility
        log.debug("Adding manual catalog item to "+mgmt+": "+item);
        checkNotNull(item, "item");
        CatalogUtils.installLibraries(mgmt, item.getLibraries());
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsCatalog.addEntry(getAbstractCatalogItem(item));
    }

    @Override @Deprecated /** @deprecated see super */
    public CatalogItem<?,?> addItem(Class<?> type) {
        //assume forceUpdate for backwards compatibility
        log.debug("Adding manual catalog item to "+mgmt+": "+type);
        checkNotNull(type, "type");
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsClasses.addClass(type);
        return manualAdditionsCatalog.classpath.addCatalogEntry(type);
    }

    private synchronized void loadManualAdditionsCatalog() {
        if (manualAdditionsCatalog!=null) return;
        CatalogDto manualAdditionsCatalogDto = CatalogDto.newNamedInstance(
                "Manual Catalog Additions", "User-additions to the catalog while Brooklyn is running, " +
                "created "+Time.makeDateString(),
                "manual-additions");
        CatalogDo manualAdditionsCatalog = catalog.addCatalog(manualAdditionsCatalogDto);
        if (manualAdditionsCatalog==null) {
            // not hard to support, but slightly messy -- probably have to use ID's to retrieve the loaded instance
            // for now block once, then retry
            log.warn("Blocking until catalog is loaded before changing it");
            boolean loaded = blockIfNotLoaded(Duration.TEN_SECONDS);
            if (!loaded)
                log.warn("Catalog still not loaded after delay; subsequent operations may fail");
            manualAdditionsCatalog = catalog.addCatalog(manualAdditionsCatalogDto);
            if (manualAdditionsCatalog==null) {
                throw new UnsupportedOperationException("Catalogs cannot be added until the base catalog is loaded, and catalog is taking a while to load!");
            }
        }
        
        log.debug("Creating manual additions catalog for "+mgmt+": "+manualAdditionsCatalog);
        manualAdditionsClasses = new LoadedClassLoader();
        ((AggregateClassLoader)manualAdditionsCatalog.classpath.getLocalClassLoader()).addFirst(manualAdditionsClasses);
        
        // expose when we're all done
        this.manualAdditionsCatalog = manualAdditionsCatalog;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T,SpecT> Iterable<CatalogItem<T,SpecT>> getCatalogItems() {
        if (!getCatalog().isLoaded()) {
            // some callers use this to force the catalog to load (maybe when starting as hot_backup without a catalog ?)
            log.debug("Forcing catalog load on access of catalog items");
            load();
        }
        return ImmutableList.copyOf((Iterable)catalog.getIdCache().values());
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T,SpecT> Iterable<CatalogItem<T,SpecT>> getCatalogItems(Predicate<? super CatalogItem<T,SpecT>> filter) {
        Iterable<CatalogItemDo<T,SpecT>> filtered = Iterables.filter((Iterable)catalog.getIdCache().values(), (Predicate<CatalogItem<T,SpecT>>)(Predicate) filter);
        return Iterables.transform(filtered, BasicBrooklynCatalog.<T,SpecT>itemDoToDto());
    }

    private static <T,SpecT> Function<CatalogItemDo<T,SpecT>, CatalogItem<T,SpecT>> itemDoToDto() {
        return new Function<CatalogItemDo<T,SpecT>, CatalogItem<T,SpecT>>() {
            @Override
            public CatalogItem<T,SpecT> apply(@Nullable CatalogItemDo<T,SpecT> item) {
                return item.getDto();
            }
        };
    }

    transient CatalogXmlSerializer serializer;
    
    public String toXmlString() {
        if (serializer==null) loadSerializer();
        return serializer.toString(catalog.dto);
    }
    
    private synchronized void loadSerializer() {
        if (serializer==null) 
            serializer = new CatalogXmlSerializer();
    }

    public void resetCatalogToContentsAtConfiguredUrl() {
        CatalogDto dto = null;
        String catalogUrl = mgmt.getConfig().getConfig(BrooklynServerConfig.BROOKLYN_CATALOG_URL);
        try {
            if (!Strings.isEmpty(catalogUrl)) {
                dto = CatalogDto.newDtoFromUrl(catalogUrl);
                if (log.isDebugEnabled()) {
                    log.debug("Loading catalog from {}: {}", catalogUrl, catalog);
                }
            }
        } catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                Maybe<Object> nonDefaultUrl = mgmt.getConfig().getConfigRaw(BrooklynServerConfig.BROOKLYN_CATALOG_URL, true);
                if (nonDefaultUrl.isPresentAndNonNull() && !"".equals(nonDefaultUrl.get())) {
                    log.warn("Could not find catalog XML specified at {}; using default (local classpath) catalog. Error was: {}", nonDefaultUrl, e);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("No default catalog file available at {}; trying again using local classpath to populate catalog. Error was: {}", catalogUrl, e);
                    }
                }
            } else {
                log.warn("Error importing catalog XML at " + catalogUrl + "; using default (local classpath) catalog. Error was: " + e, e);
            }
        }
        if (dto == null) {
            // retry, either an error, or was blank
            dto = CatalogDto.newDefaultLocalScanningDto(CatalogClasspathDo.CatalogScanningModes.ANNOTATIONS);
            if (log.isDebugEnabled()) {
                log.debug("Loaded default (local classpath) catalog: " + catalog);
            }
        }

        reset(dto);
    }

    @Deprecated
    public CatalogItem<?,?> getCatalogItemForType(String typeName) {
        final CatalogItem<?,?> resultI;
        final BrooklynCatalog catalog = mgmt.getCatalog();
        if (CatalogUtils.looksLikeVersionedId(typeName)) {
            //All catalog identifiers of the form xxxx:yyyy are composed of symbolicName+version.
            //No javaType is allowed as part of the identifier.
            resultI = CatalogUtils.getCatalogItemOptionalVersion(mgmt, typeName);
        } else {
            //Usually for catalog items with javaType (that is items from catalog.xml)
            //the symbolicName and javaType match because symbolicName (was ID)
            //is not specified explicitly. But could be the case that there is an item
            //whose symbolicName is explicitly set to be different from the javaType.
            //Note that in the XML the attribute is called registeredTypeName.
            Iterable<CatalogItem<Object,Object>> resultL = catalog.getCatalogItems(CatalogPredicates.javaType(Predicates.equalTo(typeName)));
            if (!Iterables.isEmpty(resultL)) {
                //Push newer versions in front of the list (not that there should
                //be more than one considering the items are coming from catalog.xml).
                resultI = sortVersionsDesc(resultL).iterator().next();
                if (log.isDebugEnabled() && Iterables.size(resultL)>1) {
                    log.debug("Found "+Iterables.size(resultL)+" matches in catalog for type "+typeName+"; returning the result with preferred version, "+resultI);
                }
            } else {
                //As a last resort try searching for items with the same symbolicName supposedly
                //different from the javaType.
                resultI = catalog.getCatalogItem(typeName, BrooklynCatalog.DEFAULT_VERSION);
                if (resultI != null) {
                    if (resultI.getJavaType() == null) {
                        throw new NoSuchElementException("Unable to find catalog item for type "+typeName +
                                ". There is an existing catalog item with ID " + resultI.getId() +
                                " but it doesn't define a class type.");
                    }
                }
            }
        }
        return resultI;
    }

}
