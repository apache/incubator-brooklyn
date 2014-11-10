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

import static com.google.common.base.Preconditions.checkNotNull;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;
import io.brooklyn.camp.spi.pdp.Service;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogBundle;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.internal.EntityManagementUtils;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.AggregateClassLoader;
import brooklyn.util.javalang.LoadedClassLoader;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;
import brooklyn.util.yaml.Yamls;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class BasicBrooklynCatalog implements BrooklynCatalog {
    private static final String POLICIES_KEY = "brooklyn.policies";
    public static final String NO_VERSION = "0.0.0_SNAPSHOT";

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
        String versionedId = CatalogUtils.getVersionedId(symbolicName, version);
        CatalogItemDo<?, ?> item = null;
        //TODO should remove "manual additions" bucket; just have one map a la osgi
        if (manualAdditionsCatalog!=null) item = manualAdditionsCatalog.getIdCache().get(versionedId);
        if (item == null) item = catalog.getIdCache().get(versionedId);
        return item;
    }
    
    @Override
    @Deprecated
    public CatalogItem<?,?> getCatalogItem(String id) {
        return getCatalogItem(id, NO_VERSION);
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
        deleteCatalogItem(id, NO_VERSION);
    }

    @Override
    public void deleteCatalogItem(String id, String version) {
        log.debug("Deleting manual catalog item from "+mgmt+": "+id + ":" + version);
        checkNotNull(id, "id");
        checkNotNull(id, "version");
        CatalogItem<?, ?> item = getCatalogItem(id, version);
        CatalogItemDtoAbstract<?,?> itemDto = getAbstractCatalogItem(item);
        if (itemDto == null) {
            throw new NoSuchElementException("No catalog item found with id "+id);
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
        return getCatalogItem(type, id, NO_VERSION);
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
    public ClassLoader getRootClassLoader() {
        return catalog.getRootClassLoader();
    }

    /**
     * Loads this catalog
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
            DeploymentPlan plan = makePlanFromYaml(yaml);
            BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, item);
            SpecT spec;
            switch (item.getCatalogItemType()) {
                case TEMPLATE:
                case ENTITY:
                    spec = createEntitySpec(plan, loader);
                    break;
                case POLICY:
                    spec = createPolicySpec(plan, loader);
                    break;
                default: throw new RuntimeException("Only entity & policy catalog items are supported. Unsupported catalog item type " + item.getCatalogItemType());
            }
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
    private <T, SpecT> SpecT createEntitySpec(DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();

        // TODO should not register new AT each time we instantiate from the same plan; use some kind of cache
        AssemblyTemplate at;
        BrooklynLoaderTracker.setLoader(loader);
        try {
            at = camp.pdp().registerDeploymentPlan(plan);
        } finally {
            BrooklynLoaderTracker.unsetLoader(loader);
        }

        try {
            AssemblyTemplateInstantiator instantiator = at.getInstantiator().newInstance();
            if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
                return (SpecT) ((AssemblyTemplateSpecInstantiator)instantiator).createSpec(at, camp, loader, true);
            }
            throw new IllegalStateException("Unable to instantiate YAML; incompatible instantiator "+instantiator+" for "+at);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }


    @SuppressWarnings("unchecked")
    private <T, SpecT> SpecT createPolicySpec(DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        //Would ideally re-use io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver.PolicySpecResolver
        //but it is CAMP specific and there is no easy way to get hold of it.
        Object policies = checkNotNull(plan.getCustomAttributes().get(POLICIES_KEY), "policy config");
        if (!(policies instanceof Iterable<?>)) {
            throw new IllegalStateException("The value of " + POLICIES_KEY + " must be an Iterable.");
        }

        Object policy = Iterables.getOnlyElement((Iterable<?>)policies);

        Map<String, Object> policyConfig;
        if (policy instanceof String) {
            policyConfig = ImmutableMap.<String, Object>of("type", policy);
        } else if (policy instanceof Map) {
            policyConfig = (Map<String, Object>) policy;
        } else {
            throw new IllegalStateException("Policy exepcted to be string or map. Unsupported object type " + policy.getClass().getName() + " (" + policy.toString() + ")");
        }

        String policyType = (String) checkNotNull(Yamls.getMultinameAttribute(policyConfig, "policy_type", "policyType", "type"), "policy type");
        Map<String, Object> brooklynConfig = (Map<String, Object>) policyConfig.get("brooklyn.config");
        PolicySpec<? extends Policy> spec = PolicySpec.create(loader.loadClass(policyType, Policy.class));
        if (brooklynConfig != null) {
            spec.configure(brooklynConfig);
        }
        return (SpecT) spec;
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
        Iterable<CatalogItem<Object,Object>> resultL = getCatalogItems(CatalogPredicates.javaType(Predicates.equalTo(typeName)));
        if (Iterables.isEmpty(resultL)) throw new NoSuchElementException("Unable to find catalog item for type "+typeName);
        CatalogItem<?,?> resultI = resultL.iterator().next();
        if (log.isDebugEnabled() && Iterables.size(resultL)>1) {
            log.debug("Found "+Iterables.size(resultL)+" matches in catalog for type "+typeName+"; returning the first, "+resultI);
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

    private CatalogItemDtoAbstract<?,?> getAbstractCatalogItem(String yaml) {
        DeploymentPlan plan = makePlanFromYaml(yaml);

        Collection<CatalogBundle> libraries = Collections.emptyList();

        @SuppressWarnings("rawtypes")
        Maybe<Map> possibleCatalog = plan.getCustomAttribute("brooklyn.catalog", Map.class, true);
        MutableMap<String, Object> catalog = MutableMap.of();
        if (possibleCatalog.isPresent()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog2 = (Map<String, Object>) possibleCatalog.get();
            catalog.putAll(catalog2);
        }

        Maybe<Object> possibleLibraries = catalog.getMaybe("libraries");
        if (possibleLibraries.isAbsent()) possibleLibraries = catalog.getMaybe("brooklyn.libraries");
        if (possibleLibraries.isPresentAndNonNull()) {
            if (!(possibleLibraries.get() instanceof Collection))
                throw new IllegalArgumentException("Libraries should be a list, not "+possibleLibraries.get());
            libraries = CatalogItemDtoAbstract.parseLibraries((Collection<?>) possibleLibraries.get());
        }

        String symbolicName;
        String version = null;

        symbolicName = (String) catalog.getMaybe("symbolicName").orNull();
        if (Strings.isBlank(symbolicName)) {
            symbolicName = (String) catalog.getMaybe("id").orNull();
            if (Strings.isNonBlank(symbolicName) && symbolicName.indexOf(CatalogUtils.VERSION_DELIMITER) != -1) {
                symbolicName = CatalogUtils.getIdFromVersionedId(symbolicName);
                version = CatalogUtils.getVersionFromVersionedId(symbolicName);
            }
        }
        if (Strings.isBlank(symbolicName))
            symbolicName = (String) catalog.getMaybe("name").orNull();
        // take name from plan if not specified in brooklyn.catalog section not supplied
        if (Strings.isBlank(symbolicName)) {
            symbolicName = plan.getName();
            if (Strings.isBlank(symbolicName)) {
                if (plan.getServices().size()==1) {
                    Service svc = Iterables.getOnlyElement(plan.getServices());
                    symbolicName = svc.getServiceType();
                }
            }
        }

        Maybe<Object> possibleVersion = catalog.getMaybe("version");
        if (possibleVersion.isAbsent() && Strings.isBlank(version)) {
            throw new IllegalArgumentException("'version' attribute missing in 'brooklyn.catalog' section.");
        } else if (possibleVersion.isPresent()) {
            if (Strings.isNonBlank(version)) {
                throw new IllegalArgumentException("Can't use both attribute 'version' and versioned id");
            }
            //could be coalesced to a number - can be one of Integer, Double, String
            version = possibleVersion.get().toString();
        }

        CatalogUtils.installLibraries(mgmt, libraries);

        AbstractBrooklynObjectSpec<?, ?> spec = createSpec(plan, CatalogUtils.newClassLoadingContext(mgmt, CatalogUtils.getVersionedId(symbolicName, version), libraries, getRootClassLoader()));

        CatalogItemBuilder<?> builder = createItemBuilder(spec, symbolicName, version)
            .libraries(libraries)
            .displayName(plan.getName())
            .description(plan.getDescription())
            .plan(yaml);

        // and populate other fields
        Maybe<Object> name = catalog.getMaybe("displayName");
        if (name.isAbsent()) name = catalog.getMaybe("name");
        if (name.isAbsent()) name = Maybe.<Object>fromNullable(plan.getName());
        if (name.isPresent()) builder.displayName((String) name.get());

        Maybe<Object> description = catalog.getMaybe("description");
        if (description.isPresent()) builder.description((String)description.get());

        Maybe<Object> iconUrl = catalog.getMaybe("iconUrl");
        if (iconUrl.isAbsent()) iconUrl = catalog.getMaybe("icon_url");
        if (iconUrl.isPresent()) builder.iconUrl((String)iconUrl.get());

        CatalogItemDtoAbstract<?, ?> dto = builder.build();
        // Overwrite generated ID
        dto.setManagementContext((ManagementContextInternal) mgmt);
        return dto;
    }

    private AbstractBrooklynObjectSpec<?,?> createSpec(DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        if (isPolicyPlan(plan)) {
            return createPolicySpec(plan, loader);
        } else {
            return createEntitySpec(plan, loader);
        }
    }

    private CatalogItemBuilder<?> createItemBuilder(AbstractBrooklynObjectSpec<?, ?> spec, String itemId, String version) {
        if (spec instanceof EntitySpec) {
            if (isApplicationSpec((EntitySpec<?>)spec)) {
                return CatalogItemBuilder.newTemplate(itemId, version);
            } else {
                return CatalogItemBuilder.newEntity(itemId, version);
            }
        } else if (spec instanceof PolicySpec) {
            return CatalogItemBuilder.newPolicy(itemId, version);
        } else {
            throw new IllegalStateException("Unknown spec type " + spec.getClass().getName() + " (" + spec + ")");
        }
    }

    private boolean isApplicationSpec(EntitySpec<?> spec) {
        return !Boolean.TRUE.equals(spec.getConfig().get(EntityManagementUtils.WRAPPER_APP_MARKER));
    }

    private boolean isPolicyPlan(DeploymentPlan plan) {
        return plan.getCustomAttributes().containsKey(POLICIES_KEY);
    }

    private DeploymentPlan makePlanFromYaml(String yaml) {
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();
        return camp.pdp().parseDeploymentPlan(Streams.newReaderWithContents(yaml));
    }

    @Override
    public CatalogItem<?,?> addItem(String yaml) {
        return addItem(yaml, false);
    }

    @Override
    public CatalogItem<?,?> addItem(String yaml, boolean forceUpdate) {
        log.debug("Adding manual catalog item to "+mgmt+": "+yaml);
        checkNotNull(yaml, "yaml");
        CatalogItemDtoAbstract<?,?> itemDto = getAbstractCatalogItem(yaml);
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
                    itemDto.getId() + ":" + itemDto.getVersion() + ". Use forceUpdate argument to override.");
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
}
