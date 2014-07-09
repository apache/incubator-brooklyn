package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.VanillaSoftwareProcess;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicRegionsFabric;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.FlagUtils.FlagConfigKeyAndValueRecord;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/** this converts PlatformComponentTemplate instances whose type is prefixed "brooklyn:"
 * to Brooklyn EntitySpec instances.
 * but TODO this should probably be done by {@link BrooklynEntityMatcher} 
 * so we have a spec by the time we come to instantiate.
 * (currently privileges "brooklyn.*" key names are checked in both places!)  
 */
public class BrooklynComponentTemplateResolver {

    final ManagementContext mgmt;
    final ConfigBag attrs;
    final Maybe<AbstractResource> template;
    final BrooklynYamlTypeLoader.Factory loader;
    AtomicBoolean alreadyBuilt = new AtomicBoolean(false);

    public static class Factory {

        /** returns resolver type based on the service type, inspecting the arguments in order to determine the service type */
        private static Class<? extends BrooklynComponentTemplateResolver> computeResolverType(String knownServiceType, AbstractResource optionalTemplate, ConfigBag attrs) {
            String type = getDeclaredType(knownServiceType, optionalTemplate, attrs);
            if (type!=null) {
                if (type.startsWith("brooklyn:") || type.startsWith("java:")) return BrooklynComponentTemplateResolver.class;
                if (type.equalsIgnoreCase("chef") || type.startsWith("chef:")) return ChefComponentTemplateResolver.class;
                // TODO other BrooklynComponentTemplateResolver subclasses detected here 
                // (perhaps use regexes mapping to subclass name, defined in mgmt?)
            }
            
            return null;
        }

        public static BrooklynComponentTemplateResolver newInstance(ManagementContext mgmt, Map<String, Object> childAttrs) {
            return newInstance(mgmt, ConfigBag.newInstance(childAttrs), null);
        }

        public static BrooklynComponentTemplateResolver newInstance(ManagementContext mgmt, AbstractResource template) {
            return newInstance(mgmt, ConfigBag.newInstance(template.getCustomAttributes()), template);
        }
        
        public static BrooklynComponentTemplateResolver newInstance(ManagementContext mgmt, String serviceType) {
            return newInstance(mgmt, ConfigBag.newInstance().configureStringKey("serviceType", serviceType), null);
        }
        
        private static BrooklynComponentTemplateResolver newInstance(ManagementContext mgmt, ConfigBag attrs, AbstractResource optionalTemplate) {
            Class<? extends BrooklynComponentTemplateResolver> rt = computeResolverType(null, optionalTemplate, attrs);
            if (rt==null) // use default 
                rt = BrooklynComponentTemplateResolver.class;
            
            try {
                return (BrooklynComponentTemplateResolver) rt.getConstructors()[0].newInstance(mgmt, attrs, optionalTemplate);
            } catch (Exception e) { throw Exceptions.propagate(e); }
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
            return BrooklynYamlTypeLoader.LoaderFromKey.extractTypeName("service", attrs).orNull();
        }

        public static boolean supportsType(ManagementContext mgmt, String serviceType) {
            Class<? extends BrooklynComponentTemplateResolver> type = computeResolverType(serviceType, null, null);
            if (type!=null) return true;
            // can't tell by a prefix; try looking it up
            try {
                newInstance(mgmt, serviceType).loadEntityClass();
                return true;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                return false;
            }
        }
    }

    public BrooklynComponentTemplateResolver(ManagementContext mgmt, ConfigBag attrs, AbstractResource optionalTemplate) {
        this.mgmt = mgmt;
        this.attrs = ConfigBag.newInstanceCopying(attrs);
        this.template = Maybe.fromNullable(optionalTemplate);
        this.loader = new BrooklynYamlTypeLoader.Factory(mgmt, this);
    }
    
    protected String getDeclaredType() {
        return Factory.getDeclaredType(null, template.orNull(), attrs);
    }
    
    protected String getJavaType() {
        String type = getDeclaredType();
        type = Strings.removeFromStart(type, "brooklyn:", "java:");
        
        // TODO currently a hardcoded list of aliases; would like that to come from mgmt somehow
        if (type.equals("cluster") || type.equals("Cluster")) return DynamicCluster.class.getName();
        if (type.equals("fabric") || type.equals("Fabric")) return DynamicRegionsFabric.class.getName();
        if (type.equals("vanilla") || type.equals("Vanilla")) return VanillaSoftwareProcess.class.getName();
        if (type.equals("web-app-cluster") || type.equals("WebAppCluster"))
            // TODO use service discovery; currently included as string to avoid needing a reference to it
            return "brooklyn.entity.webapp.ControlledDynamicWebAppCluster";
        
        return type;
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Entity> Class<T> loadEntityClass() {
        return (Class<T>) loader.type(getJavaType()).getType(Entity.class);
    }

    public <T extends Entity> EntitySpec<T> resolveSpec() {
        return resolveSpec(this.<T>loadEntityClass(), null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T extends Entity> EntitySpec<T> resolveSpec(Class<T> type, Class<? extends T> optionalImpl) {
        if (alreadyBuilt.getAndSet(true))
            throw new IllegalStateException("Spec can only be used once: "+this);

        EntitySpec<T> spec;
        if (optionalImpl != null) {
            spec = EntitySpec.create(type).impl(optionalImpl);
        } else if (type.isInterface()) {
            spec = EntitySpec.create(type);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            Class interfaceclazz = (Application.class.isAssignableFrom(type)) ? Application.class : Entity.class;
            List<Class<?>> additionalInterfaceClazzes = Reflections.getAllInterfaces(type);
            spec = EntitySpec.create(interfaceclazz).impl(type).additionalInterfaces(additionalInterfaceClazzes);
        }

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
        
        if (!Strings.isBlank(name))
            spec.displayName(name);
        if (templateId != null)
            spec.configure(BrooklynCampConstants.TEMPLATE_ID, templateId);
        if (planId != null)
            spec.configure(BrooklynCampConstants.PLAN_ID, planId);
        
        List<Location> childLocations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(attrs.getAllConfig(), true);
        if (childLocations != null)
            spec.locations(childLocations);
        
        decorateSpec(spec);
        
        return spec;
    }

    protected <T extends Entity> void decorateSpec(EntitySpec<T> spec) {
        new BrooklynEntityDecorationResolver.PolicySpecResolver(loader).decorate(spec, attrs);
        new BrooklynEntityDecorationResolver.EnricherSpecResolver(loader).decorate(spec, attrs);
        new BrooklynEntityDecorationResolver.InitializerResolver(loader).decorate(spec, attrs);
        
        configureEntityConfig(spec);
    }

    /** returns new *uninitialised* entity, with just a few of the pieces from the spec;
     * initialisation occurs soon after, in {@link #initEntity(ManagementContext, Entity, EntitySpec)},
     * inside an execution context and after entity ID's are recognised
     */
    protected <T extends Entity> T newEntity(EntitySpec<T> spec) {
        Class<? extends T> entityImpl = (spec.getImplementation() != null) ? spec.getImplementation() : mgmt.getEntityManager().getEntityTypeRegistry().getImplementedBy(spec.getType());
        InternalEntityFactory entityFactory = ((ManagementContextInternal)mgmt).getEntityFactory();
        T entity = entityFactory.constructEntity(entityImpl, spec);
        if (entity instanceof AbstractApplication) {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("mgmt", mgmt), entity);
        }

        // TODO Some of the code below could go into constructEntity?
        if (spec.getId() != null) {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", spec.getId()), entity);
        }
        String planId = (String)spec.getConfig().get(BrooklynCampConstants.PLAN_ID.getConfigKey());
        if (planId != null) {
            ((EntityInternal)entity).setConfig(BrooklynCampConstants.PLAN_ID, planId);
        }
        ((ManagementContextInternal)mgmt).prePreManage(entity);
        ((AbstractEntity)entity).setManagementContext((ManagementContextInternal)mgmt);
        
        ((AbstractEntity)entity).setProxy(entityFactory.createEntityProxy(spec, entity));
        
        if (spec.getLocations().size() > 0) {
            ((AbstractEntity)entity).addLocations(spec.getLocations());
        }
        
        if (spec.getParent() != null) entity.setParent(spec.getParent());
        
        return entity;
    }

    @SuppressWarnings("unchecked")
    private void configureEntityConfig(EntitySpec<?> spec) {
        ConfigBag bag = ConfigBag.newInstance((Map<Object, Object>) attrs.getStringKey("brooklyn.config"));
        
        List<FlagConfigKeyAndValueRecord> records = FlagUtils.findAllFlagsAndConfigKeys(null, spec.getType(), bag);
        Set<String> keyNamesUsed = new LinkedHashSet<String>();
        for (FlagConfigKeyAndValueRecord r: records) {
            if (r.getFlagMaybeValue().isPresent()) {
                Object transformed = new SpecialFlagsTransformer(mgmt).transformSpecialFlags(r.getFlagMaybeValue().get(), mgmt);
                spec.configure(r.getFlagName(), transformed);
                keyNamesUsed.add(r.getFlagName());
            }
            if (r.getConfigKeyMaybeValue().isPresent()) {
                Object transformed = new SpecialFlagsTransformer(mgmt).transformSpecialFlags(r.getConfigKeyMaybeValue().get(), mgmt);
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
                Object transformed = new SpecialFlagsTransformer(mgmt).transformSpecialFlags(bag.getStringKey(key), mgmt);
                spec.configure(ConfigKeys.newConfigKey(Object.class, key.toString()), transformed);
            }
        }
    }

    protected static class SpecialFlagsTransformer implements Function<Object, Object> {
        final ManagementContext mgmt;
        public SpecialFlagsTransformer(ManagementContext mgmt) {
            this.mgmt = mgmt;
        }
        public Object apply(Object input) {
            if (input instanceof Map)
                return transformSpecialFlags((Map<?, ?>)input, mgmt);
            else if (input instanceof Set<?>)
                return MutableSet.of(transformSpecialFlags((Iterable<?>)input, mgmt));
            else if (input instanceof List<?>)
                return MutableList.copyOf(transformSpecialFlags((Iterable<?>)input, mgmt));
            else if (input instanceof Iterable<?>)
                return transformSpecialFlags((Iterable<?>)input, mgmt);
            else 
                return transformSpecialFlags((Object)input, mgmt);
        }
        
        protected Map<?, ?> transformSpecialFlags(Map<?, ?> flag, final ManagementContext mgmt) {
            return Maps.transformValues(flag, this);
        }
        
        protected Iterable<?> transformSpecialFlags(Iterable<?> flag, final ManagementContext mgmt) {
            return Iterables.transform(flag, this);
        }
        
        /**
         * Makes additional transformations to the given flag with the extra knowledge of the flag's management context.
         * @return The modified flag, or the flag unchanged.
         */
        protected Object transformSpecialFlags(Object flag, ManagementContext mgmt) {
            if (flag instanceof EntitySpecConfiguration) {
                EntitySpecConfiguration specConfig = (EntitySpecConfiguration) flag;
                // TODO: This should called from BrooklynAssemblyTemplateInstantiator.configureEntityConfig
                // And have transformSpecialFlags(Object flag, ManagementContext mgmt) drill into the Object flag if it's a map or iterable?
                @SuppressWarnings("unchecked")
                Map<String, Object> resolvedConfig = (Map<String, Object>)transformSpecialFlags(specConfig.getSpecConfiguration(), mgmt);
                specConfig.setSpecConfiguration(resolvedConfig);
                return Factory.newInstance(mgmt, specConfig.getSpecConfiguration()).resolveSpec();
            }
            return flag;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getChildren(Map<String, Object> attrs) {
        if (attrs==null) return null;
        return (List<Map<String, Object>>) attrs.get("brooklyn.children");
    }

}
