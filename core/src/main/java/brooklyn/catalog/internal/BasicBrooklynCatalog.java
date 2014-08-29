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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class BasicBrooklynCatalog implements BrooklynCatalog {
    private static final String POLICIES_KEY = "brooklyn.policies";

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

    public BasicBrooklynCatalog(final ManagementContext mgmt, final CatalogDto dto) {
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
        CatalogDo catalog = new CatalogDo(mgmt, dto);
        log.debug("Resetting "+this+" catalog to "+dto);
        catalog.load(mgmt, null);
        log.debug("Reloaded catalog for "+this+", now switching");
        this.catalog = catalog;
    }
    
    public CatalogDo getCatalog() {
        return catalog;
    }

    protected CatalogItemDo<?,?> getCatalogItemDo(String id) {
        return catalog.getCache().get(id);
    }
    
    @Override
    public CatalogItem<?,?> getCatalogItem(String id) {
        if (id==null) return null;
        CatalogItemDo<?,?> itemDo = getCatalogItemDo(id);
        if (itemDo==null) return null;
        return itemDo.getDto();
    }
    
    @Override
    public void deleteCatalogItem(String id) {
        log.debug("Deleting manual catalog item from "+mgmt+": "+id);
        checkNotNull(id, "id");
        CatalogItem<?, ?> item = getCatalogItem(id);
        CatalogItemDtoAbstract<?,?> itemDto = getAbstractCatalogItem(item);
        if (itemDto == null) {
            throw new NoSuchElementException("No catalog item found with id "+id);
        }
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsCatalog.deleteEntry(itemDto);
        
        // Ensure the cache is de-populated
        getCatalog().removeEntry(itemDto);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T,SpecT> CatalogItem<T,SpecT> getCatalogItem(Class<T> type, String id) {
        if (id==null) return null;
        CatalogItem<?,?> result = getCatalogItem(id);
        if (result==null) return null;
        if (type==null || type.isAssignableFrom(result.getCatalogItemJavaType())) 
            return (CatalogItem<T,SpecT>)result;
        return null;
    }
    
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
        CatalogItemDo<T,SpecT> loadedItem = (CatalogItemDo<T, SpecT>) getCatalogItemDo(item.getId());

        Class<SpecT> specType = loadedItem.getSpecType();
        if (specType==null) return null;

        String yaml = loadedItem.getPlanYaml();
        SpecT spec = null;

        if (yaml!=null) {
            DeploymentPlan plan = makePlanFromYaml(yaml);
            switch (item.getCatalogItemType()) {
                case ENTITY:return createEntitySpec(loadedItem, plan);
                case POLICY:return createPolicySpec(loadedItem, plan);
                default: throw new RuntimeException("Only entity & policy catalog items are supported. Unsupported catalog item type " + item.getCatalogItemType());
            }

        }

        // revert to legacy mechanism
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
    private <T, SpecT> SpecT createEntitySpec(
            CatalogItemDo<T, SpecT> loadedItem, DeploymentPlan plan) {
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();

        // TODO should not register new AT each time we instantiate from the same plan; use some kind of cache
        AssemblyTemplate at;
        BrooklynClassLoadingContext loader = loadedItem.newClassLoadingContext(mgmt);
        BrooklynLoaderTracker.setLoader(loader);
        try {
            at = camp.pdp().registerDeploymentPlan(plan);
        } finally {
            BrooklynLoaderTracker.unsetLoader(loader);
        }

        try {
            AssemblyTemplateInstantiator instantiator = at.getInstantiator().newInstance();
            if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
                return (SpecT) ((AssemblyTemplateSpecInstantiator)instantiator).createSpec(at, camp, loader);
            }
            throw new IllegalStateException("Unable to instantiate YAML; incompatible instantiator "+instantiator+" for "+at);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }


    @SuppressWarnings("unchecked")
    private <T, SpecT> SpecT createPolicySpec(CatalogItemDo<T, SpecT> loadedItem, DeploymentPlan plan) {
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
        BrooklynClassLoadingContext loader = loadedItem.newClassLoadingContext(mgmt);
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
        CatalogItemDo<?,?> loadedItem = getCatalogItemDo(item.getId());
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

        CatalogLibrariesDto libraries = null;

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
            if (!(possibleLibraries.get() instanceof List))
                throw new IllegalArgumentException("Libraries should be a list, not "+possibleLibraries.get());
            libraries = CatalogLibrariesDto.fromList((List<?>) possibleLibraries.get());
        }

        // TODO clear up the abundance of id, name, registered type, java type
        String registeredTypeName = (String) catalog.getMaybe("id").orNull();
        if (Strings.isBlank(registeredTypeName))
            registeredTypeName = (String) catalog.getMaybe("name").orNull();
        // take name from plan if not specified in brooklyn.catalog section not supplied
        if (Strings.isBlank(registeredTypeName)) {
            registeredTypeName = plan.getName();
            if (Strings.isBlank(registeredTypeName)) {
                if (plan.getServices().size()==1) {
                    Service svc = Iterables.getOnlyElement(plan.getServices());
                    registeredTypeName = svc.getServiceType();
                }
            }
        }

        // TODO long-term:  support applications / templates

        CatalogItemBuilder<?> builder = createItemBuilder(plan, registeredTypeName)
            .libraries(libraries)
            .name(plan.getName())
            .description(plan.getDescription())
            .plan(yaml);

        // and populate other fields
        Maybe<Object> name = catalog.getMaybe("name");
        if (name.isPresent()) builder.name((String)name.get());

        Maybe<Object> description = catalog.getMaybe("description");
        if (description.isPresent()) builder.description((String)description.get());

        Maybe<Object> iconUrl = catalog.getMaybe("iconUrl");
        if (iconUrl.isAbsent()) iconUrl = catalog.getMaybe("icon_url");
        if (iconUrl.isPresent()) builder.iconUrl((String)iconUrl.get());

        // TODO #3 support version info

        return builder.build();
    }

    private CatalogItemBuilder<?> createItemBuilder(DeploymentPlan plan, String registeredTypeName) {
        boolean isPolicy = plan.getCustomAttributes().containsKey(POLICIES_KEY);
        if (isPolicy) {
            return CatalogItemBuilder.newPolicy(registeredTypeName);
        } else {
            return CatalogItemBuilder.newEntity(registeredTypeName);
        }
    }

    private DeploymentPlan makePlanFromYaml(String yaml) {
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();
        return camp.pdp().parseDeploymentPlan(Streams.newReaderWithContents(yaml));
    }

    @Override
    public CatalogItem<?,?> addItem(String yaml) {
        log.debug("Adding manual catalog item to "+mgmt+": "+yaml);
        checkNotNull(yaml, "yaml");
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        CatalogItemDtoAbstract<?,?> itemDto = getAbstractCatalogItem(yaml);
        manualAdditionsCatalog.addEntry(itemDto);
        
        // Load the libraries now.
        // Otherwise, when CAMP looks up BrooklynEntityMatcher.accepts then it
        // won't know about this bundle:class (via the catalog item's
        // BrooklynClassLoadingContext) so will reject it as not-for-brooklyn.
        new CatalogLibrariesDo(itemDto.getLibrariesDto()).load(mgmt);

        // Ensure the cache is populated
        getCatalog().addEntry(itemDto);

        return itemDto;
    }

    @Override @Deprecated /** @deprecated see super */
    public void addItem(CatalogItem<?,?> item) {
        log.debug("Adding manual catalog item to "+mgmt+": "+item);
        checkNotNull(item, "item");
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsCatalog.addEntry(getAbstractCatalogItem(item));
    }

    @Override @Deprecated /** @deprecated see super */
    public CatalogItem<?,?> addItem(Class<?> type) {
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
        return ImmutableList.copyOf((Iterable)catalog.getCache().values());
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T,SpecT> Iterable<CatalogItem<T,SpecT>> getCatalogItems(Predicate<? super CatalogItem<T,SpecT>> filter) {
        Iterable<CatalogItemDo<T,SpecT>> filtered = Iterables.filter((Iterable)catalog.getCache().values(), (Predicate<CatalogItem<T,SpecT>>)(Predicate) filter);
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

}