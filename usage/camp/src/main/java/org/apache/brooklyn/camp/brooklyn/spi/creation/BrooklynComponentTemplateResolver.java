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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.service.CampServiceSpecResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.service.ServiceTypeResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.service.ServiceTypeResolverAdaptor;
import org.apache.brooklyn.camp.spi.AbstractResource;
import org.apache.brooklyn.camp.spi.ApplicationComponentTemplate;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.mgmt.BrooklynTags;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.core.resolve.ServiceSpecResolver;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.FlagUtils.FlagConfigKeyAndValueRecord;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * This generates instances of a template resolver that use a {@link ServiceTypeResolver}
 * to parse the {@code serviceType} line in the template.
 */
@SuppressWarnings("deprecation")  // Because of ServiceTypeResolver
public class BrooklynComponentTemplateResolver {

    private static final Logger log = LoggerFactory.getLogger(BrooklynComponentTemplateResolver.class);

    private final BrooklynClassLoadingContext loader;
    private final ManagementContext mgmt;
    private final ConfigBag attrs;
    private final Maybe<AbstractResource> template;
    private final BrooklynYamlTypeInstantiator.Factory yamlLoader;
    private final String type;
    private final AtomicBoolean alreadyBuilt = new AtomicBoolean(false);
    private final ServiceSpecResolver serviceSpecResolver;

    private BrooklynComponentTemplateResolver(BrooklynClassLoadingContext loader, ConfigBag attrs, AbstractResource optionalTemplate, String type) {
        this.loader = loader;
        this.mgmt = loader.getManagementContext();
        this.attrs = ConfigBag.newInstanceCopying(attrs);
        this.template = Maybe.fromNullable(optionalTemplate);
        this.yamlLoader = new BrooklynYamlTypeInstantiator.Factory(loader, this);
        this.type = type;
        this.serviceSpecResolver = new CampServiceSpecResolver(mgmt, getServiceTypeResolverOverrides());
    }

    // Deprecated because want to keep as much of the state private as possible
    // Can't remove them because used by ServiceTypeResolver implementations
    /** @deprecated since 0.9.0 */
    @Deprecated public ManagementContext getManagementContext() { return mgmt; }
    @Deprecated public ConfigBag getAttrs() { return attrs; }
    @Deprecated public BrooklynYamlTypeInstantiator.Factory getYamlLoader() { return yamlLoader; }
    @Deprecated public String getDeclaredType() { return type; }

    public static class Factory {

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
            String type = getDeclaredType(null, optionalTemplate, attrs);
            return new BrooklynComponentTemplateResolver(context, attrs, optionalTemplate, type);
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
        return serviceSpecResolver.accepts(type, loader);
    }

    public <T extends Entity> EntitySpec<T> resolveSpec(Set<String> encounteredCatalogTypes) {
        if (alreadyBuilt.getAndSet(true))
            throw new IllegalStateException("Spec can only be used once: "+this);

        EntitySpec<?> spec = serviceSpecResolver.resolve(type, loader, encounteredCatalogTypes);

        if (spec == null) {
            // Try to provide some troubleshooting details
            final String msgDetails;
            CatalogItem<?, ?> item = CatalogUtils.getCatalogItemOptionalVersion(mgmt, Strings.removeFromStart(type, "catalog:"));
            String proto = Urls.getProtocol(type);
            if (item != null && encounteredCatalogTypes.contains(item.getSymbolicName())) {
                msgDetails = "Cycle between catalog items detected, starting from " + type +
                        ". Other catalog items being resolved up the stack are " + encounteredCatalogTypes +
                        ". Tried loading it as a Java class instead but failed.";
            } else if (proto != null) {
                msgDetails = "The reference " + type + " looks like a URL (running the CAMP Brooklyn assembly-template instantiator) but the protocol " +
                        proto + " isn't white listed (" + BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST + "). " +
                        "Not a catalog item or java type as well.";
            } else {
                msgDetails = "No resolver knew how to handle it. Using resolvers: " + serviceSpecResolver;
            }
            throw new IllegalStateException("Unable to create spec for type " + type + ". " + msgDetails);
        }

        populateSpec(spec, encounteredCatalogTypes);

        @SuppressWarnings("unchecked")
        EntitySpec<T> typedSpec = (EntitySpec<T>) spec;
        return typedSpec;
    }

    private List<ServiceSpecResolver> getServiceTypeResolverOverrides() {
        List<ServiceSpecResolver> overrides = new ArrayList<>();
        ServiceLoader<ServiceTypeResolver> loader = ServiceLoader.load(ServiceTypeResolver.class, mgmt.getCatalogClassLoader());
        for (ServiceTypeResolver resolver : loader) {
           overrides.add(new ServiceTypeResolverAdaptor(this, resolver));
        }
        return overrides;
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> void populateSpec(EntitySpec<T> spec, Set<String> encounteredCatalogTypes) {
        String name, source=null, templateId=null, planId=null;
        if (template.isPresent()) {
            name = template.get().getName();
            templateId = template.get().getId();
            source = template.get().getSourceCode();
        } else {
            name = (String)attrs.getStringKey("name");
        }
        planId = (String)attrs.getStringKey("id");
        if (planId==null)
            planId = (String) attrs.getStringKey(BrooklynCampConstants.PLAN_ID_FLAG);

        Object childrenObj = attrs.getStringKey(BrooklynCampReservedKeys.BROOKLYN_CHILDREN);
        if (childrenObj != null) {
            Iterable<Map<String,?>> children = (Iterable<Map<String,?>>)childrenObj;
            for (Map<String,?> childAttrs : children) {
                BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(loader, childAttrs);
                // encounteredCatalogTypes must contain the items currently being loaded (the dependency chain),
                // but not parent items in this catalog item already resolved.
                EntitySpec<? extends Entity> childSpec = entityResolver.resolveSpec(encounteredCatalogTypes);
                spec.child(childSpec);
            }
        }

        if (source!=null)
            spec.tag(BrooklynTags.newYamlSpecTag(source));

        if (!Strings.isBlank(name))
            spec.displayName(name);
        if (templateId != null)
            spec.configure(BrooklynCampConstants.TEMPLATE_ID, templateId);
        if (planId != null)
            spec.configure(BrooklynCampConstants.PLAN_ID, planId);

        List<Location> childLocations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(attrs.getAllConfig(), true);
        if (childLocations != null)
            spec.locations(childLocations);

        decoreateSpec(spec);
    }

    private <T extends Entity> void decoreateSpec(EntitySpec<T> spec) {
        new BrooklynEntityDecorationResolver.PolicySpecResolver(yamlLoader).decorate(spec, attrs);
        new BrooklynEntityDecorationResolver.EnricherSpecResolver(yamlLoader).decorate(spec, attrs);
        new BrooklynEntityDecorationResolver.InitializerResolver(yamlLoader).decorate(spec, attrs);

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
                return Factory.newInstance(getLoader(), specConfig.getSpecConfiguration()).resolveSpec(ImmutableSet.<String>of());
            }
            if (flag instanceof ManagementContextInjectable) {
                log.debug("Injecting Brooklyn management context info object: {}", flag);
                ((ManagementContextInjectable) flag).injectManagementContext(loader.getManagementContext());
            }

            return flag;
        }
    }
}
