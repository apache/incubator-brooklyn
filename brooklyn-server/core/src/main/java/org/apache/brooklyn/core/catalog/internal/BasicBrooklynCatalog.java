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
package org.apache.brooklyn.core.catalog.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.catalog.CatalogPredicates;
import org.apache.brooklyn.core.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.typereg.BrooklynTypePlanTransformer;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.javalang.AggregateClassLoader;
import org.apache.brooklyn.util.javalang.LoadedClassLoader;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.apache.brooklyn.util.yaml.Yamls;
import org.apache.brooklyn.util.yaml.Yamls.YamlExtract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

/* TODO the complex tree-structured catalogs are only useful when we are relying on those separate catalog classloaders
 * to isolate classpaths. with osgi everything is just put into the "manual additions" catalog. */
public class BasicBrooklynCatalog implements BrooklynCatalog {
    public static final String POLICIES_KEY = "brooklyn.policies";
    public static final String LOCATIONS_KEY = "brooklyn.locations";
    public static final String NO_VERSION = "0.0.0.SNAPSHOT";

    private static final Logger log = LoggerFactory.getLogger(BasicBrooklynCatalog.class);

    public static class BrooklynLoaderTracker {
        public static final ThreadLocal<BrooklynClassLoadingContext> loader = new ThreadLocal<BrooklynClassLoadingContext>();
        
        public static void setLoader(BrooklynClassLoadingContext val) {
            loader.set(val);
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
    private final AggregateClassLoader rootClassLoader = AggregateClassLoader.newInstanceWithNoLoaders();

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
        reset(dto, true);
    }

    public void reset(CatalogDto dto, boolean failOnLoadError) {
        // Unregister all existing persisted items.
        for (CatalogItem<?, ?> toRemove : getCatalogItems()) {
            if (log.isTraceEnabled()) {
                log.trace("Scheduling item for persistence removal: {}", toRemove.getId());
            }
            mgmt.getRebindManager().getChangeListener().onUnmanaged(toRemove);
        }
        CatalogDo catalog = new CatalogDo(mgmt, dto);
        CatalogUtils.logDebugOrTraceIfRebinding(log, "Resetting "+this+" catalog to "+dto);
        catalog.load(mgmt, null, failOnLoadError);
        CatalogUtils.logDebugOrTraceIfRebinding(log, "Reloaded catalog for "+this+", now switching");
        this.catalog = catalog;
        resetRootClassLoader();
        this.manualAdditionsCatalog = null;

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

        return catalog.getIdCache().get( CatalogUtils.getVersionedId(symbolicName, fixedVersionId) );
    }
    
    private String getFixedVersionId(String symbolicName, String version) {
        if (version!=null && !DEFAULT_VERSION.equals(version)) {
            return version;
        } else {
            return getBestVersion(symbolicName);
        }
    }

    /** returns best version, as defined by {@link BrooklynCatalog#getCatalogItem(String, String)} */
    private String getBestVersion(String symbolicName) {
        Iterable<CatalogItem<Object, Object>> versions = getCatalogItems(Predicates.and(
                CatalogPredicates.disabled(false),
                CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName))));
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
    public CatalogItem<?,?> getCatalogItem(String symbolicName, String version) {
        if (symbolicName == null) return null;
        checkNotNull(version, "version");
        CatalogItemDo<?, ?> itemDo = getCatalogItemDo(symbolicName, version);
        if (itemDo == null) return null;
        return itemDo.getDto();
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
        if (itemDto.getCatalogItemType() == CatalogItemType.LOCATION) {
            @SuppressWarnings("unchecked")
            CatalogItem<Location,LocationSpec<?>> locationItem = (CatalogItem<Location, LocationSpec<?>>) itemDto;
            ((BasicLocationRegistry)mgmt.getLocationRegistry()).removeDefinedLocation(locationItem);
        }
        mgmt.getRebindManager().getChangeListener().onUnmanaged(itemDto);

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
        if (rootClassLoader.isEmpty() && catalog!=null) {
            resetRootClassLoader();
        }
        return rootClassLoader;
    }

    private void resetRootClassLoader() {
        rootClassLoader.reset(ImmutableList.of(catalog.getRootClassLoader()));
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

    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createSpec(CatalogItem<T, SpecT> item) {
        if (item == null) return null;
        @SuppressWarnings("unchecked")
        CatalogItemDo<T,SpecT> loadedItem = (CatalogItemDo<T, SpecT>) getCatalogItemDo(item.getSymbolicName(), item.getVersion());
        if (loadedItem == null) throw new RuntimeException(item+" not in catalog; cannot create spec");
        if (loadedItem.getSpecType()==null) return null;

        SpecT spec = internalCreateSpecLegacy(mgmt, loadedItem, MutableSet.<String>of(), true);
        if (spec != null) {
            return spec;
        }

        throw new IllegalStateException("No known mechanism to create instance of "+item);
    }
    
    /** @deprecated since introduction in 0.9.0, only used for backwards compatibility, can be removed any time;
     * uses the type-creation info on the item.
     * deprecated transformers must be included by routines which don't use {@link BrooklynTypePlanTransformer} instances;
     * otherwise deprecated transformers should be excluded. (deprecation is taken as equivalent to having a new-style transformer.) */
    @Deprecated 
    public static <T,SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT internalCreateSpecLegacy(ManagementContext mgmt, final CatalogItem<T, SpecT> item, final Set<String> encounteredTypes, boolean includeDeprecatedTransformers) {
        // deprecated lookup
        if (encounteredTypes.contains(item.getSymbolicName())) {
            throw new IllegalStateException("Type being resolved '"+item.getSymbolicName()+"' has already been encountered in " + encounteredTypes + "; recursive cycle detected");
        }
        Maybe<SpecT> specMaybe = org.apache.brooklyn.core.plan.PlanToSpecFactory.attemptWithLoaders(mgmt, includeDeprecatedTransformers, new Function<org.apache.brooklyn.core.plan.PlanToSpecTransformer, SpecT>() {
            @Override
            public SpecT apply(org.apache.brooklyn.core.plan.PlanToSpecTransformer input) {
                return input.createCatalogSpec(item, encounteredTypes);
            }
        });
        return specMaybe.get();
    }

    @Deprecated /** @deprecated since 0.7.0 only used by other deprecated items */ 
    private <T,SpecT> CatalogItemDtoAbstract<T,SpecT> getAbstractCatalogItem(CatalogItem<T,SpecT> item) {
        while (item instanceof CatalogItemDo) item = ((CatalogItemDo<T,SpecT>)item).itemDto;
        if (item==null) return null;
        if (item instanceof CatalogItemDtoAbstract) return (CatalogItemDtoAbstract<T,SpecT>) item;
        throw new IllegalStateException("Cannot unwrap catalog item '"+item+"' (type "+item.getClass()+") to restore DTO");
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Maybe<T> getFirstAs(Map<?,?> map, Class<T> type, String firstKey, String ...otherKeys) {
        if (map==null) return Maybe.absent("No map available");
        String foundKey = null;
        Object value = null;
        if (map.containsKey(firstKey)) foundKey = firstKey;
        else for (String key: otherKeys) {
            if (map.containsKey(key)) {
                foundKey = key;
                break;
            }
        }
        if (foundKey==null) return Maybe.absent("Missing entry '"+firstKey+"'");
        value = map.get(foundKey);
        if (type.equals(String.class) && Number.class.isInstance(value)) value = value.toString();
        if (!type.isInstance(value)) 
            throw new IllegalArgumentException("Entry for '"+firstKey+"' should be of type "+type+", not "+value.getClass());
        return Maybe.of((T)value);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Maybe<Map<?,?>> getFirstAsMap(Map<?,?> map, String firstKey, String ...otherKeys) {
        return (Maybe<Map<?,?>>)(Maybe) getFirstAs(map, Map.class, firstKey, otherKeys);
    }

    private List<CatalogItemDtoAbstract<?,?>> collectCatalogItems(String yaml) {
        Map<?,?> itemDef = Yamls.getAs(Yamls.parseAll(yaml), Map.class);
        Map<?,?> catalogMetadata = getFirstAsMap(itemDef, "brooklyn.catalog").orNull();
        if (catalogMetadata==null)
            log.warn("No `brooklyn.catalog` supplied in catalog request; using legacy mode for "+itemDef);
        catalogMetadata = MutableMap.copyOf(catalogMetadata);

        List<CatalogItemDtoAbstract<?, ?>> result = MutableList.of();
        
        collectCatalogItems(Yamls.getTextOfYamlAtPath(yaml, "brooklyn.catalog").getMatchedYamlTextOrWarn(), 
            catalogMetadata, result, null);
        
        itemDef.remove("brooklyn.catalog");
        catalogMetadata.remove("item");
        catalogMetadata.remove("items");
        if (!itemDef.isEmpty()) {
            log.debug("Reading brooklyn.catalog peer keys as item ('top-level syntax')");
            Map<String,?> rootItem = MutableMap.of("item", itemDef);
            String rootItemYaml = yaml;
            YamlExtract yamlExtract = Yamls.getTextOfYamlAtPath(rootItemYaml, "brooklyn.catalog");
            String match = yamlExtract.withOriginalIndentation(true).withKeyIncluded(true).getMatchedYamlTextOrWarn();
            if (match!=null) {
                if (rootItemYaml.startsWith(match)) rootItemYaml = Strings.removeFromStart(rootItemYaml, match);
                else rootItemYaml = Strings.replaceAllNonRegex(rootItemYaml, "\n"+match, "");
            }
            collectCatalogItems("item:\n"+makeAsIndentedObject(rootItemYaml), rootItem, result, catalogMetadata);
        }
        
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectCatalogItems(String sourceYaml, Map<?,?> itemMetadata, List<CatalogItemDtoAbstract<?, ?>> result, Map<?,?> parentMetadata) {

        if (sourceYaml==null) sourceYaml = new Yaml().dump(itemMetadata);

        Map<Object,Object> catalogMetadata = MutableMap.builder().putAll(parentMetadata).putAll(itemMetadata).build();
        
        // brooklyn.libraries we treat specially, to append the list, with the child's list preferred in classloading order
        // `libraries` is supported in some places as a legacy syntax; it should always be `brooklyn.libraries` for new apps
        // TODO in 0.8.0 require brooklyn.libraries, don't allow "libraries" on its own
        List<?> librariesNew = MutableList.copyOf(getFirstAs(itemMetadata, List.class, "brooklyn.libraries", "libraries").orNull());
        Collection<CatalogBundle> libraryBundlesNew = CatalogItemDtoAbstract.parseLibraries(librariesNew);
        
        List<?> librariesCombined = MutableList.copyOf(librariesNew)
            .appendAll(getFirstAs(parentMetadata, List.class, "brooklyn.libraries", "libraries").orNull());
        if (!librariesCombined.isEmpty())
            catalogMetadata.put("brooklyn.libraries", librariesCombined);
        Collection<CatalogBundle> libraryBundles = CatalogItemDtoAbstract.parseLibraries(librariesCombined);

        // TODO as this may take a while if downloading, the REST call should be async
        // (this load is required for the scan below and I think also for yaml resolution)
        CatalogUtils.installLibraries(mgmt, libraryBundlesNew);

        Boolean scanJavaAnnotations = getFirstAs(itemMetadata, Boolean.class, "scanJavaAnnotations", "scan_java_annotations").orNull();
        if (scanJavaAnnotations==null || !scanJavaAnnotations) {
            // don't scan
        } else {
            // scan for annotations: if libraries here, scan them; if inherited libraries error; else scan classpath
            if (!libraryBundlesNew.isEmpty()) {
                result.addAll(scanAnnotationsFromBundles(mgmt, libraryBundlesNew, catalogMetadata));
            } else if (libraryBundles.isEmpty()) {
                result.addAll(scanAnnotationsFromLocal(mgmt, catalogMetadata));
            } else {
                throw new IllegalStateException("Cannot scan catalog node no local bundles, and with inherited bundles we will not scan the classpath");
            }
        }
        
        Object items = catalogMetadata.remove("items");
        Object item = catalogMetadata.remove("item");

        if (items!=null) {
            int count = 0;
            for (Map<?,?> i: ((List<Map<?,?>>)items)) {
                collectCatalogItems(Yamls.getTextOfYamlAtPath(sourceYaml, "items", count).getMatchedYamlTextOrWarn(), 
                    i, result, catalogMetadata);
                count++;
            }
        }
        
        if (item==null) return;

        // now look at the actual item, first correcting the sourceYaml and interpreting the catalog metadata
        String itemYaml = Yamls.getTextOfYamlAtPath(sourceYaml, "item").getMatchedYamlTextOrWarn();
        if (itemYaml!=null) sourceYaml = itemYaml;
        else sourceYaml = new Yaml().dump(item);
        
        CatalogItemType itemType = TypeCoercions.coerce(getFirstAs(catalogMetadata, Object.class, "itemType", "item_type").orNull(), CatalogItemType.class);

        String id = getFirstAs(catalogMetadata, String.class, "id").orNull();
        String version = getFirstAs(catalogMetadata, String.class, "version").orNull();
        String symbolicName = getFirstAs(catalogMetadata, String.class, "symbolicName").orNull();
        String displayName = getFirstAs(catalogMetadata, String.class, "displayName").orNull();
        String name = getFirstAs(catalogMetadata, String.class, "name").orNull();

        if ((Strings.isNonBlank(id) || Strings.isNonBlank(symbolicName)) && 
                Strings.isNonBlank(displayName) &&
                Strings.isNonBlank(name) && !name.equals(displayName)) {
            log.warn("Name property will be ignored due to the existence of displayName and at least one of id, symbolicName");
        }

        PlanInterpreterGuessingType planInterpreter = new PlanInterpreterGuessingType(null, item, sourceYaml, itemType, libraryBundles, result).reconstruct();
        if (!planInterpreter.isResolved()) {
            throw Exceptions.create("Could not resolve item"
                + (Strings.isNonBlank(id) ? " "+id : Strings.isNonBlank(symbolicName) ? " "+symbolicName : Strings.isNonBlank(name) ? name : "")
                // better not to show yaml, takes up lots of space, and with multiple plan transformers there might be multiple errors 
//                + ":\n"+sourceYaml
                , planInterpreter.getErrors());
        }
        itemType = planInterpreter.getCatalogItemType();
        Map<?, ?> itemAsMap = planInterpreter.getItem();
        // the "plan yaml" includes the services: ... or brooklyn.policies: ... outer key,
        // as opposed to the rawer { type: xxx } map without that outer key which is valid as item input
        // TODO this plan yaml is needed for subsequent reconstruction; would be nicer if it weren't! 

        // if symname not set, infer from: id, then name, then item id, then item name
        if (Strings.isBlank(symbolicName)) {
            if (Strings.isNonBlank(id)) {
                if (CatalogUtils.looksLikeVersionedId(id)) {
                    symbolicName = CatalogUtils.getSymbolicNameFromVersionedId(id);
                } else {
                    symbolicName = id;
                }
            } else if (Strings.isNonBlank(name)) {
                if (CatalogUtils.looksLikeVersionedId(name)) {
                    symbolicName = CatalogUtils.getSymbolicNameFromVersionedId(name);
                } else {
                    symbolicName = name;
                }
            } else {
                symbolicName = setFromItemIfUnset(symbolicName, itemAsMap, "id");
                symbolicName = setFromItemIfUnset(symbolicName, itemAsMap, "name");
                // TODO we should let the plan transformer give us this
                symbolicName = setFromItemIfUnset(symbolicName, itemAsMap, "template_name");
                if (Strings.isBlank(symbolicName)) {
                    log.error("Can't infer catalog item symbolicName from the following plan:\n" + sourceYaml);
                    throw new IllegalStateException("Can't infer catalog item symbolicName from catalog item metadata");
                }
            }
        }

        // if version not set, infer from: id, then from name, then item version
        if (CatalogUtils.looksLikeVersionedId(id)) {
            String versionFromId = CatalogUtils.getVersionFromVersionedId(id);
            if (versionFromId != null && Strings.isNonBlank(version) && !versionFromId.equals(version)) {
                throw new IllegalArgumentException("Discrepency between version set in id " + versionFromId + " and version property " + version);
            }
            version = versionFromId;
        }
        if (Strings.isBlank(version)) {
            if (CatalogUtils.looksLikeVersionedId(name)) {
                version = CatalogUtils.getVersionFromVersionedId(name);
            } else if (Strings.isBlank(version)) {
                version = setFromItemIfUnset(version, itemAsMap, "version");
                version = setFromItemIfUnset(version, itemAsMap, "template_version");
                if (version==null) {
                    log.warn("No version specified for catalog item " + symbolicName + ". Using default value.");
                    version = null;
                }
            }
        }
        
        // if not set, ID can come from symname:version, failing that, from the plan.id, failing that from the sym name
        if (Strings.isBlank(id)) {
            // let ID be inferred, especially from name, to support style where only "name" is specified, with inline version
            if (Strings.isNonBlank(symbolicName) && Strings.isNonBlank(version)) {
                id = symbolicName + ":" + version;
            }
            id = setFromItemIfUnset(id, itemAsMap, "id");
            if (Strings.isBlank(id)) {
                if (Strings.isNonBlank(symbolicName)) {
                    id = symbolicName;
                } else {
                    log.error("Can't infer catalog item id from the following plan:\n" + sourceYaml);
                    throw new IllegalStateException("Can't infer catalog item id from catalog item metadata");
                }
            }
        }

        if (Strings.isBlank(displayName)) {
            if (Strings.isNonBlank(name)) displayName = name;
            displayName = setFromItemIfUnset(displayName, itemAsMap, "name");
        }

        String description = getFirstAs(catalogMetadata, String.class, "description").orNull();
        description = setFromItemIfUnset(description, itemAsMap, "description");

        // icon.url is discouraged, but kept for legacy compatibility; should deprecate this
        final String catalogIconUrl = getFirstAs(catalogMetadata, String.class, "iconUrl", "icon_url", "icon.url").orNull();

        final String deprecated = getFirstAs(catalogMetadata, String.class, "deprecated").orNull();
        final Boolean catalogDeprecated = Boolean.valueOf(deprecated);

        // run again now that we know the ID
        planInterpreter = new PlanInterpreterGuessingType(id, item, sourceYaml, itemType, libraryBundles, result).reconstruct();
        if (!planInterpreter.isResolved()) {
            throw new IllegalStateException("Could not resolve plan once id and itemType are known (recursive reference?): "+sourceYaml);
        }
        String sourcePlanYaml = planInterpreter.getPlanYaml();

        CatalogItemDtoAbstract<?, ?> dto = createItemBuilder(itemType, symbolicName, version)
            .libraries(libraryBundles)
            .displayName(displayName)
            .description(description)
            .deprecated(catalogDeprecated)
            .iconUrl(catalogIconUrl)
            .plan(sourcePlanYaml)
            .build();

        dto.setManagementContext((ManagementContextInternal) mgmt);
        result.add(dto);
    }

    private String setFromItemIfUnset(String oldValue, Map<?,?> item, String fieldAttr) {
        if (Strings.isNonBlank(oldValue)) return oldValue;
        if (item!=null) {
            Object newValue = item.get(fieldAttr);
            if (newValue instanceof String && Strings.isNonBlank((String)newValue)) 
                return (String)newValue;
        }
        return oldValue;
    }

    private Collection<CatalogItemDtoAbstract<?, ?>> scanAnnotationsFromLocal(ManagementContext mgmt, Map<Object, Object> catalogMetadata) {
        CatalogDto dto = CatalogDto.newNamedInstance("Local Scanned Catalog", "All annotated Brooklyn entities detected in the classpath", "scanning-local-classpath");
        return scanAnnotationsInternal(mgmt, new CatalogDo(dto), catalogMetadata);
    }
    
    private Collection<CatalogItemDtoAbstract<?, ?>> scanAnnotationsFromBundles(ManagementContext mgmt, Collection<CatalogBundle> libraries, Map<Object, Object> catalogMetadata) {
        CatalogDto dto = CatalogDto.newNamedInstance("Bundles Scanned Catalog", "All annotated Brooklyn entities detected in bundles", "scanning-bundles-classpath-"+libraries.hashCode());
        List<String> urls = MutableList.of();
        for (CatalogBundle b: libraries) {
            // TODO currently does not support pre-installed bundles identified by name:version 
            // (ie where URL not supplied)
            if (Strings.isNonBlank(b.getUrl())) {
                urls.add(b.getUrl());
            }
        }
        
        if (urls.isEmpty()) {
            log.warn("No bundles to scan: scanJavaAnnotations currently only applies to OSGi bundles provided by URL"); 
            return MutableList.of();
        }
        
        CatalogDo subCatalog = new CatalogDo(dto);
        subCatalog.addToClasspath(urls.toArray(new String[0]));
        return scanAnnotationsInternal(mgmt, subCatalog, catalogMetadata);
    }
    
    private Collection<CatalogItemDtoAbstract<?, ?>> scanAnnotationsInternal(ManagementContext mgmt, CatalogDo subCatalog, Map<Object, Object> catalogMetadata) {
        // TODO this does java-scanning only;
        // the call when scanning bundles should use the CatalogItem instead and use OSGi when loading for scanning
        // (or another scanning mechanism).  see comments on CatalogClasspathDo.load
        subCatalog.mgmt = mgmt;
        subCatalog.setClasspathScanForEntities(CatalogScanningModes.ANNOTATIONS);
        subCatalog.load();
        // TODO apply metadata?  (extract YAML from the items returned)
        // also see doc .../catalog/index.md which says we might not apply metadata
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Collection<CatalogItemDtoAbstract<?, ?>> result = (Collection<CatalogItemDtoAbstract<?, ?>>)(Collection)Collections2.transform(
                (Collection<CatalogItemDo<Object,Object>>)(Collection)subCatalog.getIdCache().values(), 
                itemDoToDtoAddingSelectedMetadataDuringScan(catalogMetadata));
        return result;
    }

    private class PlanInterpreterGuessingType {

        final String id;
        final Map<?,?> item;
        final String itemYaml;
        final Collection<CatalogBundle> libraryBundles;
        final List<CatalogItemDtoAbstract<?, ?>> itemsDefinedSoFar;
        
        CatalogItemType catalogItemType;
        String planYaml;
        boolean resolved = false;
        List<Exception> errors = MutableList.of();
        List<Exception> entityErrors = MutableList.of();
        
        public PlanInterpreterGuessingType(@Nullable String id, Object item, String itemYaml, @Nullable CatalogItemType optionalCiType, 
                Collection<CatalogBundle> libraryBundles, List<CatalogItemDtoAbstract<?,?>> itemsDefinedSoFar) {
            // ID is useful to prevent recursive references (possibly only supported for entities?)
            this.id = id;
            
            if (item instanceof String) {
                // if just a string supplied, wrap as map
                this.item = MutableMap.of("type", item);
                this.itemYaml = "type:\n"+makeAsIndentedObject(itemYaml);                
            } else {
                this.item = (Map<?,?>)item;
                this.itemYaml = itemYaml;
            }
            this.catalogItemType = optionalCiType;
            this.libraryBundles = libraryBundles;
            this.itemsDefinedSoFar = itemsDefinedSoFar;
        }

        public PlanInterpreterGuessingType reconstruct() {
            if (catalogItemType==CatalogItemType.TEMPLATE) {
                // template *must* be explicitly defined, and if so, none of the other calls apply
                attemptType(null, CatalogItemType.TEMPLATE);
                
            } else {
                attemptType(null, CatalogItemType.ENTITY);

                attemptType("services", CatalogItemType.ENTITY);
                attemptType(POLICIES_KEY, CatalogItemType.POLICY);
                attemptType(LOCATIONS_KEY, CatalogItemType.LOCATION);
            }
            
            if (!resolved && catalogItemType==CatalogItemType.TEMPLATE) {
                // anything goes, for an explicit template, because we can't easily recurse into the types
                planYaml = itemYaml;
                resolved = true;
            }
            
            return this;
        }

        public boolean isResolved() { return resolved; }
        
        /** Returns potentially useful errors encountered while guessing types. 
         * May only be available where the type is known. */
        public List<Exception> getErrors() {
            if (errors.isEmpty()) return entityErrors;
            return errors;
        }
        
        public CatalogItemType getCatalogItemType() {
            return catalogItemType; 
        }
        
        public String getPlanYaml() {
            return planYaml;
        }
        
        private boolean attemptType(String key, CatalogItemType candidateCiType) {
            if (resolved) return false;
            if (catalogItemType!=null && catalogItemType!=candidateCiType) return false;
            
            final String candidateYaml;
            if (key==null) candidateYaml = itemYaml;
            else {
                if (item.containsKey(key))
                    candidateYaml = itemYaml;
                else
                    candidateYaml = key + ":\n" + makeAsIndentedList(itemYaml);
            }
            // first look in collected items, if a key is given
            String type = (String) item.get("type");
            String version = null;
            if (CatalogUtils.looksLikeVersionedId(type)) {
                version = CatalogUtils.getVersionFromVersionedId(type);
                type = CatalogUtils.getSymbolicNameFromVersionedId(type);
            }
            if (type!=null && key!=null) {
                for (CatalogItemDtoAbstract<?,?> candidate: itemsDefinedSoFar) {
                    if (candidateCiType == candidate.getCatalogItemType() &&
                            (type.equals(candidate.getSymbolicName()) || type.equals(candidate.getId()))) {
                        if (version==null || version.equals(candidate.getVersion())) {
                            // matched - exit
                            catalogItemType = candidateCiType;
                            planYaml = candidateYaml;
                            resolved = true;
                            return true;
                        }
                    }
                }
            }
            
            // then try parsing plan - this will use loader
            try {
                @SuppressWarnings("rawtypes")
                CatalogItem itemToAttempt = createItemBuilder(candidateCiType, getIdWithRandomDefault(), DEFAULT_VERSION)
                    .plan(candidateYaml)
                    .libraries(libraryBundles)
                    .build();
                @SuppressWarnings("unchecked")
                AbstractBrooklynObjectSpec<?, ?> spec = internalCreateSpecLegacy(mgmt, itemToAttempt, MutableSet.<String>of(), true);
                if (spec!=null) {
                    catalogItemType = candidateCiType;
                    planYaml = candidateYaml;
                    resolved = true;
                }
                return true;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                // record the error if we have reason to expect this guess to succeed
                if (item.containsKey("services") && (candidateCiType==CatalogItemType.ENTITY || candidateCiType==CatalogItemType.TEMPLATE)) {
                    // explicit services supplied, so plan should have been parseable for an entity or a a service
                    errors.add(e);
                } else if (catalogItemType!=null && key!=null) {
                    // explicit itemType supplied, so plan should be parseable in the cases where we're given a key
                    // (when we're not given a key, the previous block should apply)
                    errors.add(e);
                } else {
                    // all other cases, the error is probably due to us not getting the type right, probably ignore it
                    // but cache it if we've checked entity, we'll use that as fallback errors
                    if (candidateCiType==CatalogItemType.ENTITY) {
                        entityErrors.add(e);
                    }
                    if (log.isTraceEnabled())
                        log.trace("Guessing type of plan, it looks like it isn't "+candidateCiType+"/"+key+": "+e);
                }
            }
            
            // finally try parsing a cut-down plan, in case there is a nested reference to a newly defined catalog item
            if (type!=null && key!=null) {
                try {
                    String cutDownYaml = key + ":\n" + makeAsIndentedList("type: "+type);
                    @SuppressWarnings("rawtypes")
                    CatalogItem itemToAttempt = createItemBuilder(candidateCiType, getIdWithRandomDefault(), DEFAULT_VERSION)
                            .plan(cutDownYaml)
                            .libraries(libraryBundles)
                            .build();
                    @SuppressWarnings("unchecked")
                    AbstractBrooklynObjectSpec<?, ?> cutdownSpec = internalCreateSpecLegacy(mgmt, itemToAttempt, MutableSet.<String>of(), true);
                    if (cutdownSpec!=null) {
                        catalogItemType = candidateCiType;
                        planYaml = candidateYaml;
                        resolved = true;
                    }
                    return true;
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                }
            }
            // FIXME we should lookup type in the catalog on its own, then infer the type from that,
            // and give proper errors (right now e.g. if there are no transformers then we bail out 
            // with very little information)
            
            return false;
        }

        private String getIdWithRandomDefault() {
            return id != null ? id : Strings.makeRandomId(10);
        }
        public Map<?,?> getItem() {
            return item;
        }
    }
    
    private String makeAsIndentedList(String yaml) {
        String[] lines = yaml.split("\n");
        lines[0] = "- "+lines[0];
        for (int i=1; i<lines.length; i++)
            lines[i] = "  " + lines[i];
        return Strings.join(lines, "\n");
    }

    private String makeAsIndentedObject(String yaml) {
        String[] lines = yaml.split("\n");
        for (int i=0; i<lines.length; i++)
            lines[i] = "  " + lines[i];
        return Strings.join(lines, "\n");
    }

    static CatalogItemBuilder<?> createItemBuilder(CatalogItemType itemType, String symbolicName, String version) {
        return CatalogItemBuilder.newItem(itemType, symbolicName, version);
    }

    // these kept as their logic may prove useful; Apr 2015
//    private boolean isApplicationSpec(EntitySpec<?> spec) {
//        return !Boolean.TRUE.equals(spec.getConfig().get(EntityManagementUtils.WRAPPER_APP_MARKER));
//    }
//
//    private boolean isEntityPlan(DeploymentPlan plan) {
//        return plan!=null && !plan.getServices().isEmpty() || !plan.getArtifacts().isEmpty();
//    }
//    
//    private boolean isPolicyPlan(DeploymentPlan plan) {
//        return !isEntityPlan(plan) && plan.getCustomAttributes().containsKey(POLICIES_KEY);
//    }
//
//    private boolean isLocationPlan(DeploymentPlan plan) {
//        return !isEntityPlan(plan) && plan.getCustomAttributes().containsKey(LOCATIONS_KEY);
//    }

    //------------------------
    
    @Override
    public CatalogItem<?,?> addItem(String yaml) {
        return addItem(yaml, false);
    }

    @Override
    public List<? extends CatalogItem<?,?>> addItems(String yaml) {
        return addItems(yaml, false);
    }

    @Override
    public CatalogItem<?,?> addItem(String yaml, boolean forceUpdate) {
        return Iterables.getOnlyElement(addItems(yaml, forceUpdate));
    }
    
    @Override
    public List<? extends CatalogItem<?,?>> addItems(String yaml, boolean forceUpdate) {
        log.debug("Adding manual catalog item to "+mgmt+": "+yaml);
        checkNotNull(yaml, "yaml");
        List<CatalogItemDtoAbstract<?, ?>> result = collectCatalogItems(yaml);

        // do this at the end for atomic updates; if there are intra-yaml references, we handle them specially
        for (CatalogItemDtoAbstract<?, ?> item: result) {
            addItemDto(item, forceUpdate);
        }
        return result;
    }
    
    private CatalogItem<?,?> addItemDto(CatalogItemDtoAbstract<?, ?> itemDto, boolean forceUpdate) {
        CatalogItem<?, ?> existingDto = checkItemAllowedAndIfSoReturnAnyDuplicate(itemDto, true, forceUpdate);
        if (existingDto!=null) {
            // it's a duplicate, and not forced, just return it
            log.trace("Using existing duplicate for catalog item {}", itemDto.getId());
            return existingDto;
        }

        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsCatalog.addEntry(itemDto);

        // Ensure the cache is populated and it is persisted by the management context
        getCatalog().addEntry(itemDto);

        // Request that the management context persist the item.
        if (log.isTraceEnabled()) {
            log.trace("Scheduling item for persistence addition: {}", itemDto.getId());
        }
        if (itemDto.getCatalogItemType() == CatalogItemType.LOCATION) {
            @SuppressWarnings("unchecked")
            CatalogItem<Location,LocationSpec<?>> locationItem = (CatalogItem<Location, LocationSpec<?>>) itemDto;
            ((BasicLocationRegistry)mgmt.getLocationRegistry()).updateDefinedLocation(locationItem);
        }
        mgmt.getRebindManager().getChangeListener().onManaged(itemDto);

        return itemDto;
    }

    /** returns item DTO if item is an allowed duplicate, or null if it should be added (there is no duplicate), 
     * throwing if item cannot be added */
    private CatalogItem<?, ?> checkItemAllowedAndIfSoReturnAnyDuplicate(CatalogItem<?,?> itemDto, boolean allowDuplicates, boolean forceUpdate) {
        if (forceUpdate) return null;
        CatalogItemDo<?, ?> existingItem = getCatalogItemDo(itemDto.getSymbolicName(), itemDto.getVersion());
        if (existingItem == null) return null;
        // check if they are equal
        CatalogItem<?, ?> existingDto = existingItem.getDto();
        if (existingDto.equals(itemDto)) {
            if (allowDuplicates) return existingItem;
            throw new IllegalStateException("Updating existing catalog entries, even with the same content, is forbidden: " +
                    itemDto.getSymbolicName() + ":" + itemDto.getVersion() + ". Use forceUpdate argument to override.");
        } else {
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
                if (item==null) return null;
                return item.getDto();
            }
        };
    }
    
    private static <T,SpecT> Function<CatalogItemDo<T, SpecT>, CatalogItem<T,SpecT>> itemDoToDtoAddingSelectedMetadataDuringScan(final Map<Object, Object> catalogMetadata) {
        return new Function<CatalogItemDo<T,SpecT>, CatalogItem<T,SpecT>>() {
            @Override
            public CatalogItem<T,SpecT> apply(@Nullable CatalogItemDo<T,SpecT> item) {
                if (item==null) return null;
                CatalogItemDtoAbstract<T, SpecT> dto = (CatalogItemDtoAbstract<T, SpecT>) item.getDto();

                // when scanning we only allow version and libraries to be overwritten
                
                String version = getFirstAs(catalogMetadata, String.class, "version").orNull();
                if (Strings.isNonBlank(version)) dto.setVersion(version);
                
                Object librariesCombined = catalogMetadata.get("brooklyn.libraries");
                if (librariesCombined instanceof Collection) {
                    // will be set by scan -- slightly longwinded way to retrieve, but scanning for osgi needs an overhaul in any case
                    Collection<CatalogBundle> libraryBundles = CatalogItemDtoAbstract.parseLibraries((Collection<?>) librariesCombined);
                    dto.setLibraries(libraryBundles);
                }
                // replace java type with plan yaml -- needed for libraries / catalog item to be picked up,
                // but probably useful to transition away from javaType altogether
                dto.setSymbolicName(dto.getJavaType());
                switch (dto.getCatalogItemType()) {
                    case TEMPLATE:
                    case ENTITY:
                        dto.setPlanYaml("services: [{ type: "+dto.getJavaType()+" }]");
                        break;
                    case POLICY:
                        dto.setPlanYaml(POLICIES_KEY + ": [{ type: "+dto.getJavaType()+" }]");
                        break;
                    case LOCATION:
                        dto.setPlanYaml(LOCATIONS_KEY + ": [{ type: "+dto.getJavaType()+" }]");
                        break;
                }
                dto.setJavaType(null);

                return dto;
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

}
