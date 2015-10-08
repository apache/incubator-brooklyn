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
package org.apache.brooklyn.camp.brooklyn.spi.creation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.service.DefaultServiceTypeResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.service.ServiceTypeResolver;
import org.apache.brooklyn.camp.spi.AbstractResource;
import org.apache.brooklyn.camp.spi.ApplicationComponentTemplate;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.BrooklynTags;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.FlagUtils.FlagConfigKeyAndValueRecord;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * This generates instances of a template resolver that use a {@link ServiceTypeResolver}
 * to parse the {@code serviceType} line in the template.
 */
public class BrooklynComponentTemplateResolver {

    private static final Logger log = LoggerFactory.getLogger(BrooklynComponentTemplateResolver.class);

    private final BrooklynClassLoadingContext loader;
    private final ManagementContext mgmt;
    private final ConfigBag attrs;
    private final Maybe<AbstractResource> template;
    private final BrooklynYamlTypeInstantiator.Factory yamlLoader;
    private final String type;
    private final ServiceTypeResolver typeResolver;
    private final AtomicBoolean alreadyBuilt = new AtomicBoolean(false);

    private BrooklynComponentTemplateResolver(BrooklynClassLoadingContext loader, ConfigBag attrs, AbstractResource optionalTemplate, String type, ServiceTypeResolver typeResolver) {
        this.loader = loader;
        this.mgmt = loader.getManagementContext();
        this.attrs = ConfigBag.newInstanceCopying(attrs);
        this.template = Maybe.fromNullable(optionalTemplate);
        this.yamlLoader = new BrooklynYamlTypeInstantiator.Factory(loader, this);
        this.type = type;
        this.typeResolver = typeResolver;
    }

    public ManagementContext getManagementContext() { return mgmt; }
    public ConfigBag getAttrs() { return attrs; }
    public BrooklynYamlTypeInstantiator.Factory getYamlLoader() { return yamlLoader; }
    public String getDeclaredType() { return type; }

    public static class Factory {

        /** returns resolver type based on the service type, inspecting the arguments in order to determine the service type */
        private static ServiceTypeResolver computeResolverType(BrooklynClassLoadingContext context, String knownServiceType, AbstractResource optionalTemplate, ConfigBag attrs) {
            String type = getDeclaredType(knownServiceType, optionalTemplate, attrs);
            return findService(context, type);
        }

        // TODO This could be extended to support multiple prefixes per resolver and a 'best-match' algorithm
        private static ServiceTypeResolver findService(BrooklynClassLoadingContext context, String type) {
            if (type.indexOf(':') != -1) {
                String prefix = Splitter.on(":").splitToList(type).get(0);
                ServiceLoader<ServiceTypeResolver> loader = ServiceLoader.load(ServiceTypeResolver.class,
                        context.getManagementContext().getCatalogClassLoader());
                for (ServiceTypeResolver resolver : loader) {
                   if (prefix.equals(resolver.getTypePrefix())) {
                       return resolver;
                   }
                }
            }
            return null;
        }

        public static BrooklynComponentTemplateResolver newInstance(BrooklynClassLoadingContext context, Map<String, ?> childAttrs) {
            return newInstance(context, ConfigBag.newInstance(childAttrs), null);
        }

        public static BrooklynComponentTemplateResolver newInstance(BrooklynClassLoadingContext context, AbstractResource template) {
            return newInstance(context, ConfigBag.newInstance(template.getCustomAttributes()), template);
        }

        public static BrooklynComponentTemplateResolver newInstance(BrooklynClassLoadingContext context, String serviceType) {
            return newInstance(context, ConfigBag.newInstance().configureStringKey("serviceType", serviceType), null);
        }

        private static BrooklynComponentTemplateResolver newInstance(BrooklynClassLoadingContext context, ConfigBag attrs, AbstractResource optionalTemplate) {
            ServiceTypeResolver typeResolver = computeResolverType(context, null, optionalTemplate, attrs);
            String type = getDeclaredType(null, optionalTemplate, attrs);
            if (typeResolver == null) // use default
                typeResolver = new DefaultServiceTypeResolver();
            return new BrooklynComponentTemplateResolver(context, attrs, optionalTemplate, type, typeResolver);
        }

        private static String getDeclaredType(String knownServiceType, AbstractResource optionalTemplate, @Nullable ConfigBag attrs) {
            String type = knownServiceType;
            if (type==null && optionalTemplate!=null) {
                type = optionalTemplate.getType();
                if (type.equals(AssemblyTemplate.CAMP_TYPE) || type.equals(PlatformComponentTemplate.CAMP_TYPE) || type.equals(ApplicationComponentTemplate.CAMP_TYPE))
                    // ignore these values for the type; only subclasses are interesting
                    type = null;
            }
            if (type==null) type = extractServiceTypeAttribute(attrs);
            return type;
        }

        private static String extractServiceTypeAttribute(@Nullable ConfigBag attrs) {
            return BrooklynYamlTypeInstantiator.InstantiatorFromKey.extractTypeName("service", attrs).orNull();
        }
    }

    public boolean canResolve() {
        if (!(typeResolver instanceof DefaultServiceTypeResolver)) {
            return true;
        }

        CatalogItem<Entity, EntitySpec<?>> item = typeResolver.getCatalogItem(this, type);
        if (item != null) {
            if (item.isDisabled()) {
                log.warn("Disallowed attempt to use disabled catalog item "+item.getId());
                return false;
            } else if (item.isDeprecated()) {
                log.warn("Use of deprecated catalog item "+item.getId());
            }
            return true;
        }

        if (tryLoadEntityClass().isPresent()) {
            return true;
        }

        String protocol = Urls.getProtocol(type);
        if (protocol != null) {
            if (BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST.contains(protocol)) {
                return true;
            } else {
                log.debug("The reference '" + type + "' looks like a URL (running the CAMP Brooklyn entity-matcher) but the protocol '" + 
                        protocol + "' isn't white listed " + BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST + ". " +
                        "Not recognized as catalog item or java item as well!");
            }
        }

        return true;
    }

    /** returns the entity class, if needed in contexts which scan its statics for example */
    private Class<? extends Entity> loadEntityClass() {
        Maybe<Class<? extends Entity>> result = tryLoadEntityClass();
        if (result.isAbsent())
            throw new IllegalStateException("Could not find "+typeResolver.getBrooklynType(type), ((Maybe.Absent<?>)result).getException());
        return result.get();
    }

    /** tries to load the Java entity class */
    private Maybe<Class<? extends Entity>> tryLoadEntityClass() {
        return loader.tryLoadClass(getJavaType(), Entity.class);
    }

    // TODO Generalise to have other prefixes (e.g. explicit "catalog:" etc)?
    private boolean isJavaTypePrefix() {
        return type != null && (type.toLowerCase().startsWith("java:") || type.toLowerCase().startsWith("brooklyn:java:"));
    }

    private String getJavaType() {
        CatalogItem<Entity, EntitySpec<?>> item = typeResolver.getCatalogItem(this, type);
        if (!isJavaTypePrefix() && item != null && item.getJavaType() != null) {
            return item.getJavaType();
        } else {
            return typeResolver.getBrooklynType(type);
        }
    }

    public <T extends Entity> EntitySpec<T> resolveSpec(Set<String> encounteredCatalogTypes, boolean canUseOtherTransformers) {
        if (alreadyBuilt.getAndSet(true))
            throw new IllegalStateException("Spec can only be used once: "+this);

        String brooklynType = typeResolver.getBrooklynType(type);
        CatalogItem<Entity, EntitySpec<?>> item = typeResolver.getCatalogItem(this, type);

        if (log.isTraceEnabled()) log.trace("Building CAMP template services: type="+brooklynType+"; item="+item+"; loader="+loader+"; encounteredCatalogTypes="+encounteredCatalogTypes);

        // TODO implement as service type
        EntitySpec<T> spec = null;
        String protocol = Urls.getProtocol(brooklynType);
        if (protocol != null) {
            if (BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST.contains(protocol)) {
                spec = tryResolveYamlUrlReferenceSpec(brooklynType, encounteredCatalogTypes);
                if (spec != null) {
                    populateSpec(spec);
                }
            } else {
                // TODO this will probably be logged if we refer to  chef:cookbook  or other service types which BCTR accepts;
                // better would be to have BCTR supporting the calls above
                log.debug("The reference " + brooklynType + " looks like a URL (running the CAMP Brooklyn assembly-template instantiator) but the protocol " +
                        protocol + " isn't white listed (" + BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST + "). " +
                        "Will try to load it as catalog item or java type.");
            }
        }

        if (spec == null) {
            // load from java or yaml
            spec = resolveLocalSpec(encounteredCatalogTypes, canUseOtherTransformers);
        }

        return spec;
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> EntitySpec<T> tryResolveYamlUrlReferenceSpec(String brooklynType, Set<String> encounteredCatalogTypes) {
        String yaml;
        try {
            yaml = ResourceUtils.create(this).getResourceAsString(brooklynType);
        } catch (Exception e) {
            log.warn("AssemblyTemplate type " + brooklynType + " which looks like a URL can't be fetched.", e);
            return null;
        }
        // Referenced specs are expected to be CAMP format as well.
        List<EntitySpec<?>> serviceSpecs = CampUtils.createServiceSpecs(yaml, loader, encounteredCatalogTypes);
        if (serviceSpecs.size() > 1) {
            throw new UnsupportedOperationException("Only supporting single service in remotely referenced plans: got "+serviceSpecs);
        }
        return (EntitySpec<T>) serviceSpecs.get(0);
    }

    private <T extends Entity> EntitySpec<T> resolveLocalSpec(Set<String> encounteredCatalogTypes, boolean canUseOtherTransformers) {
        CatalogItem<Entity, EntitySpec<?>> item = typeResolver.getCatalogItem(this, type);
        EntitySpec<T> spec = createSpec(item, encounteredCatalogTypes);
        populateSpec(spec);
        return spec;
    }

    @SuppressWarnings({ "unchecked" })
    private <T extends Entity,SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> EntitySpec<T> createSpec(CatalogItem<Entity, EntitySpec<?>> item, Set<String> encounteredCatalogTypes) {
        if (item == null) {
            // ignore; presumably a java type or some such?
        } else if (item.isDisabled()) {
            throw new IllegalStateException("Illegal use of disabled catalog item "+item.getSymbolicName()+":"+item.getVersion());
        } else if (item.isDeprecated()) {
            log.warn("Use of deprecated catalog item "+item.getSymbolicName()+":"+item.getVersion());
        }
        
        if (encounteredCatalogTypes==null) encounteredCatalogTypes = MutableSet.of();
        
        //Take the symbolicName part of the catalog item only for recursion detection to prevent
        //cross referencing of different versions. Not interested in non-catalog item types.
        //Prevent catalog items self-referencing even if explicitly different version.
        boolean firstOccurrence = (item == null || encounteredCatalogTypes.add(item.getSymbolicName()));
        boolean recursiveButTryJava = !firstOccurrence;

        // Load a java class from current loader if explicit java prefix, or if no item, or if item is legacy / 
        // old-style catalog item (item != null && item.getJavaType() != null).
        // Old-style catalog items (can be defined in catalog.xml only) don't have structure, only a single type, so
        // they are loaded as a simple java type, only taking the class name from the catalog item instead of the
        // type value in the YAML. Classpath entries in the item are also used (through the catalog root classloader).
        if (isJavaTypePrefix() || item == null || item.getJavaType() != null) {
            return createSpecFromJavaType();

        // Same as above case, but this time force java type loading (either as plain class or through an old-style
        // catalog item, since we have already loaded a class item with the same name as the type value.
        } else if (recursiveButTryJava) {
            if (tryLoadEntityClass().isAbsent()) {
                throw new IllegalStateException("Recursive reference to " + item + " (and cannot be resolved as a Java type)");
            }
            return createSpecFromJavaType();

        // Only case that's left is a catalog item with CAMP YAML content - try to parse it recursively
        // including its OSGi bundles in the loader classpath.
        } else {
            return (EntitySpec<T>) EntityManagementUtils.createCatalogSpec(mgmt, (CatalogItem<T,SpecT>)item, encounteredCatalogTypes);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Entity> EntitySpec<T> createSpecFromJavaType() {
        Class<T> type = (Class<T>) loadEntityClass();
        
        EntitySpec<T> spec;
        if (type.isInterface()) {
            spec = EntitySpec.create(type);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            @SuppressWarnings("rawtypes")
            Class interfaceclazz = (Application.class.isAssignableFrom(type)) ? Application.class : Entity.class;
            List<Class<?>> additionalInterfaceClazzes = Reflections.getAllInterfaces(type);
            spec = EntitySpec.create(interfaceclazz).impl(type).additionalInterfaces(additionalInterfaceClazzes);
        }
        spec.catalogItemId(CatalogUtils.getCatalogItemIdFromLoader(loader));
        if (template.isPresent() && template.get().getSourceCode()!=null)
            spec.tag(BrooklynTags.newYamlSpecTag(template.get().getSourceCode()));

        return spec;
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> void populateSpec(EntitySpec<T> spec) {
        String name, templateId=null, planId=null;
        if (template.isPresent()) {
            name = template.get().getName();
            templateId = template.get().getId();
        } else {
            name = (String)attrs.getStringKey("name");
        }
        planId = (String)attrs.getStringKey("id");
        if (planId==null)
            planId = (String) attrs.getStringKey(BrooklynCampConstants.PLAN_ID_FLAG);

        Object childrenObj = attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_CHILDREN);
        if (childrenObj != null) {
            // Creating a new set of encounteredCatalogTypes means that this won't check things recursively;
            // but we are looking at children so we probably *should* be resetting the recursive list we've looked at;
            // (but see also, a previous comment here which suggested otherwise? - Apr 2015)
            Set<String> encounteredCatalogTypes = MutableSet.of();

            Iterable<Map<String,?>> children = (Iterable<Map<String,?>>)childrenObj;
            for (Map<String,?> childAttrs : children) {
                BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(loader, childAttrs);
                EntitySpec<? extends Entity> childSpec = entityResolver.resolveSpec(encounteredCatalogTypes, true);
                spec.child(childSpec);
            }
        }
        if (!Strings.isBlank(name))
            spec.displayName(name);
        if (templateId != null)
            spec.configure(BrooklynCampConstants.TEMPLATE_ID, templateId);
        if (planId != null)
            spec.configure(BrooklynCampConstants.PLAN_ID, planId);

        List<Location> childLocations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(attrs.getAllConfig(), true);
        if (childLocations != null)
            spec.locations(childLocations);

        typeResolver.decorateSpec(this, spec);
        configureEntityConfig(spec);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void configureEntityConfig(EntitySpec<?> spec) {
        // first take *recognised* flags and config keys from the top-level, and put them in the bag (of brooklyn.config)
        // attrs will contain only brooklyn.xxx properties when coming from BrooklynEntityMatcher.
        // Any top-level flags will go into "brooklyn.flags". When resolving a spec from $brooklyn:entitySpec
        // top level flags remain in place. Have to support both cases.

        ConfigBag bag = ConfigBag.newInstance((Map<Object, Object>) attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_CONFIG));
        ConfigBag bagFlags = ConfigBag.newInstanceCopying(attrs);
        if (attrs.containsKey(BrooklynCampReservedKeys.BROOKLYN_FLAGS)) {
            bagFlags.putAll((Map<String, Object>) attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_FLAGS));
        }

        Collection<FlagConfigKeyAndValueRecord> topLevelApparentConfig = findAllFlagsAndConfigKeys(spec, bagFlags);
        for (FlagConfigKeyAndValueRecord r: topLevelApparentConfig) {
            if (r.getConfigKeyMaybeValue().isPresent())
                bag.putIfAbsent((ConfigKey)r.getConfigKey(), r.getConfigKeyMaybeValue().get());
            if (r.getFlagMaybeValue().isPresent())
                bag.putAsStringKeyIfAbsent(r.getFlagName(), r.getFlagMaybeValue().get());
        }

        // now set configuration for all the items in the bag
        Collection<FlagConfigKeyAndValueRecord> records = findAllFlagsAndConfigKeys(spec, bag);
        Set<String> keyNamesUsed = new LinkedHashSet<String>();
        for (FlagConfigKeyAndValueRecord r: records) {
            if (r.getFlagMaybeValue().isPresent()) {
                Object transformed = new SpecialFlagsTransformer(loader).apply(r.getFlagMaybeValue().get());
                spec.configure(r.getFlagName(), transformed);
                keyNamesUsed.add(r.getFlagName());
            }
            if (r.getConfigKeyMaybeValue().isPresent()) {
                Object transformed = new SpecialFlagsTransformer(loader).apply(r.getConfigKeyMaybeValue().get());
                spec.configure((ConfigKey<Object>)r.getConfigKey(), transformed);
                keyNamesUsed.add(r.getConfigKey().getName());
            }
        }

        // set unused keys as anonymous config keys -
        // they aren't flags or known config keys, so must be passed as config keys in order for
        // EntitySpec to know what to do with them (as they are passed to the spec as flags)
        for (String key: MutableSet.copyOf(bag.getUnusedConfig().keySet())) {
            // we don't let a flag with the same name as a config key override the config key
            // (that's why we check whether it is used)
            if (!keyNamesUsed.contains(key)) {
                Object transformed = new SpecialFlagsTransformer(loader).apply(bag.getStringKey(key));
                spec.configure(ConfigKeys.newConfigKey(Object.class, key.toString()), transformed);
            }
        }
    }

    /**
     * Searches for config keys in the type, additional interfaces and the implementation (if specified)
     */
    private Collection<FlagConfigKeyAndValueRecord> findAllFlagsAndConfigKeys(EntitySpec<?> spec, ConfigBag bagFlags) {
        Set<FlagConfigKeyAndValueRecord> allKeys = MutableSet.of();
        allKeys.addAll(FlagUtils.findAllFlagsAndConfigKeys(null, spec.getType(), bagFlags));
        if (spec.getImplementation() != null) {
            allKeys.addAll(FlagUtils.findAllFlagsAndConfigKeys(null, spec.getImplementation(), bagFlags));
        }
        for (Class<?> iface : spec.getAdditionalInterfaces()) {
            allKeys.addAll(FlagUtils.findAllFlagsAndConfigKeys(null, iface, bagFlags));
        }
        return allKeys;
    }

    private static class SpecialFlagsTransformer implements Function<Object, Object> {
        protected final ManagementContext mgmt;
        /* TODO find a way to make do without loader here?
         * it is not very nice having to serialize it; but serialization of BLCL is now relatively clean.
         *
         * it is only used to instantiate classes, and now most things should be registered with catalog;
         * the notable exception is when one entity in a bundle is creating another in the same bundle,
         * it wants to use his bundle CLC to do that.  but we can set up some unique reference to the entity
         * which can be used to find it from mgmt, rather than pass the loader.
         */
        private BrooklynClassLoadingContext loader = null;

        public SpecialFlagsTransformer(BrooklynClassLoadingContext loader) {
            this.loader = loader;
            mgmt = loader.getManagementContext();
        }
        @Override
        public Object apply(Object input) {
            if (input instanceof Map)
                return transformSpecialFlags((Map<?, ?>)input);
            else if (input instanceof Set<?>)
                return MutableSet.of(transformSpecialFlags((Iterable<?>)input));
            else if (input instanceof List<?>)
                return MutableList.copyOf(transformSpecialFlags((Iterable<?>)input));
            else if (input instanceof Iterable<?>)
                return transformSpecialFlags((Iterable<?>)input);
            else
                return transformSpecialFlags((Object)input);
        }

        protected Map<?, ?> transformSpecialFlags(Map<?, ?> flag) {
            return Maps.transformValues(flag, this);
        }

        protected Iterable<?> transformSpecialFlags(Iterable<?> flag) {
            return Iterables.transform(flag, this);
        }

        protected BrooklynClassLoadingContext getLoader() {
            if (loader!=null) return loader;
            // TODO currently loader will non-null unless someone has messed with the rebind files,
            // but we'd like to get rid of it; ideally we'd have a reference to the entity.
            // for now, this is a slightly naff way to do it, if we have to set loader=null as a workaround
            Entity entity = BrooklynTaskTags.getTargetOrContextEntity(Tasks.current());
            if (entity!=null) return CatalogUtils.getClassLoadingContext(entity);
            return JavaBrooklynClassLoadingContext.create(mgmt);
        }

        /**
         * Makes additional transformations to the given flag with the extra knowledge of the flag's management context.
         * @return The modified flag, or the flag unchanged.
         */
        protected Object transformSpecialFlags(Object flag) {
            if (flag instanceof EntitySpecConfiguration) {
                EntitySpecConfiguration specConfig = (EntitySpecConfiguration) flag;
                // TODO: This should called from BrooklynAssemblyTemplateInstantiator.configureEntityConfig
                // And have transformSpecialFlags(Object flag, ManagementContext mgmt) drill into the Object flag if it's a map or iterable?
                @SuppressWarnings("unchecked")
                Map<String, Object> resolvedConfig = (Map<String, Object>)transformSpecialFlags(specConfig.getSpecConfiguration());
                specConfig.setSpecConfiguration(resolvedConfig);
                return Factory.newInstance(getLoader(), specConfig.getSpecConfiguration()).resolveLocalSpec(null, false);
            }
            if (flag instanceof ManagementContextInjectable) {
                log.debug("Injecting Brooklyn management context info object: {}", flag);
                ((ManagementContextInjectable) flag).injectManagementContext(loader.getManagementContext());
            }

            return flag;
        }
    }
}
