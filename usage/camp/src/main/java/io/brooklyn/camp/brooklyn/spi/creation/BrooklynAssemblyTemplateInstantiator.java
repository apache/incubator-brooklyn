package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import io.brooklyn.camp.brooklyn.spi.platform.HasBrooklynManagementContext;
import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BrooklynAssemblyTemplateInstantiator implements AssemblyTemplateInstantiator {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAssemblyTemplateInstantiator.class);
    
    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        Application app = create(template, platform);
        Task<?> task = start(app, platform);
        log.info("CAMP created "+app+"; starting in "+task);
        return ((BrooklynCampPlatform)platform).assemblies().get(app.getApplicationId());
    }

    // note: based on BrooklynRestResourceUtils, but modified to not allow child entities (yet)
    // (will want to revise that when building up from a non-brooklyn template)
    public Application create(AssemblyTemplate template, CampPlatform platform) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);
        
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();
        CatalogItem<?> item = catalog.getCatalogItem(template.getId());

        if (item==null) {
            return createApplicationFromNonCatalogCampTemplate(template, platform);
        } else {
            return createApplicationFromCatalog(platform, item, template);
        }
    }

    protected Application createApplicationFromCatalog(CampPlatform platform, CatalogItem<?> item, AssemblyTemplate template) {
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();

        if (!template.getApplicationComponentTemplates().isEmpty() ||
                !template.getPlatformComponentTemplates().isEmpty())
            log.warn("CAMP AssemblyTemplate was not empty when creating from catalog spec; ignoring templates declared within it " +
                    "("+template+")");

        // TODO name (and description) -- not prescribed by camp spec (cf discussion with gil)
        String name = template.getName();
                
        String type = item.getJavaType();
        final Application instance;

        // Load the class; first try to use the appropriate catalog item; but then allow anything that is on the classpath
        final Class<? extends Entity> clazz;
        if (Strings.isEmpty(type)) {
            clazz = BasicApplication.class;
        } else {
            clazz = loadEntityType(catalog, type);
        }
        
        try {
            if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor();
                ApplicationBuilder appBuilder = (ApplicationBuilder) constructor.newInstance();
                
                if (!Strings.isEmpty(name)) appBuilder.appDisplayName(name);
        
                // TODO use buildEntityConfig instead
                final Map<?,?> configO = (Map<?,?>) template.getCustomAttributes().get("brooklyn.config");

                log.info("REST placing '{}' under management", appBuilder);
                appBuilder.configure( convertFlagsToKeys(appBuilder.getType(), configO) );
                instance = appBuilder.manage(mgmt);
                
                List<Location> locations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(template.getCustomAttributes(), false);
                if (locations!=null)
                    ((EntityInternal)instance).addLocations(locations);
                
            } else if (Application.class.isAssignableFrom(clazz)) {
                // TODO use buildEntityConfig instead
                final Map<?,?> configO = (Map<?,?>) template.getCustomAttributes().get("brooklyn.config");
                
                brooklyn.entity.proxying.EntitySpec<?> coreSpec = toCoreEntitySpec(clazz, name, configO);
                instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);

                log.info("REST placing '{}' under management", instance);
                Entities.startManagement(instance, mgmt);
                
                List<Location> locations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(template.getCustomAttributes(), false);
                if (locations!=null)
                    ((EntityInternal)instance).addLocations(locations);
                
            } else {
                throw new IllegalArgumentException("Class "+clazz+" must extend one of ApplicationBuilder or Application");
            }
            
            return instance;
            
        } catch (Exception e) {
            log.error("CAMP failed to create application: "+e, e);
            throw Exceptions.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected Class<? extends Entity> loadEntityType(BrooklynCatalog catalog, String type) {
        final Class<? extends Entity> clazz;
        Class<? extends Entity> tempclazz;
        try {
            tempclazz = catalog.loadClassByType(type, Entity.class);
        } catch (NoSuchElementException e) {
            try {
                tempclazz = (Class<? extends Entity>) catalog.getRootClassLoader().loadClass(type);
                log.debug("Catalog does not contain item for type {}; loaded class directly instead", type);
            } catch (ClassNotFoundException e2) {
                log.warn("No catalog item for type {}, and could not load class directly; rethrowing", type);
                throw e;
            }
        }
        clazz = tempclazz;
        return clazz;
    }

    @SuppressWarnings("unchecked")
    protected <T> Class<T> loadClass(BrooklynCatalog catalog, String type) throws ClassNotFoundException {
        try {
            Class<T> result = (Class<T>) catalog.getRootClassLoader().loadClass(type);
            log.debug("Loaded class {} directly", type);
            return result;
        } catch (ClassNotFoundException e) {
            log.warn("Could not load class {} directly; rethrowing", type);
            throw e;
        }
    }

    private ManagementContext getBrooklynManagementContext(CampPlatform platform) {
        ManagementContext mgmt = ((HasBrooklynManagementContext)platform).getBrooklynManagementContext();
        return mgmt;
    }
    
    public Task<?> start(Application app, CampPlatform platform) {
        return Entities.invokeEffector((EntityLocal)app, app, Startable.START,
            // locations already set in the entities themselves;
            // TODO make it so that this arg does not have to be supplied to START !
            MutableMap.of("locations", MutableList.of()));
    }

    // TODO exact copy of BrooklynRestResoureUtils
    private static Map<?,?> convertFlagsToKeys(Class<? extends Entity> javaType, Map<?, ?> config) {
        if (config==null || config.isEmpty() || javaType==null) return config;

        Map<String, ConfigKey<?>> configKeys = EntityTypes.getDefinedConfigKeys(javaType);
        Map<Object,Object> result = new LinkedHashMap<Object,Object>();
        for (Map.Entry<?,?> entry: config.entrySet()) {
            log.debug("Setting key {} to {} for REST creation of {}", new Object[] { entry.getKey(), entry.getValue(), javaType});
            Object key = configKeys.get(entry.getKey());
            if (key==null) {
                log.warn("Unrecognised config key {} passed to {}; will be treated as flag (and likely ignored)", entry.getKey(), javaType);
                key = entry.getKey();
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    // TODO exact copy of BRRU
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends Entity> brooklyn.entity.proxying.EntitySpec<?> toCoreEntitySpec(Class<T> clazz, String name, Map<?,?> configO) {
        Map<?, ?> config = (configO == null) ? Maps.<Object,Object>newLinkedHashMap() : Maps.newLinkedHashMap(configO);
        
        EntitySpec result;
        if (clazz.isInterface()) {
            result = EntitySpec.create(clazz);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            Class interfaceclazz = (Application.class.isAssignableFrom(clazz)) ? Application.class : Entity.class;
            Class<?>[] additionalInterfaceClazzes = clazz.getInterfaces();
            result = EntitySpec.create(interfaceclazz).impl(clazz).additionalInterfaces(additionalInterfaceClazzes);
        }
        
        if (!Strings.isEmpty(name)) result.displayName(name);
        result.configure( convertFlagsToKeys(result.getImplementation(), config) );
        return result;
    }

    protected <T extends Entity> T newEntity(ManagementContext mgmt, EntitySpec<T> spec) {
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
        
        if (spec.getParent() != null) entity.setParent(spec.getParent());
        
        return entity;
    }

    protected <T extends Entity> void initEntity(ManagementContext mgmt, T entity, EntitySpec<T> spec) {
        InternalEntityFactory entityFactory = ((ManagementContextInternal)mgmt).getEntityFactory();
        entityFactory.initEntity(entity, spec);
    }

    protected <T extends Entity> EntitySpec<T> buildSpec(ManagementContext mgmt, Class<T> type, Map<String, Object> attrsOrig) {
        Map<String, Object> attrs = MutableMap.copyOf(attrsOrig);
        String name = (String)attrs.remove("name");
        String id = (String)attrs.remove("id");
        List<Location> locations = new BrooklynYamlLocationResolver(mgmt).resolveLocations((Map<String, Object>)attrs, true);
        
        EntitySpec<T> spec = EntitySpec.create(type);
        if (!Strings.isBlank(name))
            spec.displayName(name);
        if (id != null)
            spec.configure(BrooklynCampConstants.PLAN_ID, id);
        if (locations != null)
            spec.locations(locations);
        
        spec.policySpecs(buildPolicySpecs(mgmt, attrs.remove("brooklyn.policies")));
        spec.enricherSpecs(buildEnricherSpecs(mgmt, attrs.remove("brooklyn.enrichers")));
        spec.configure(buildEntityConfig(attrs));
        
        return spec;
    }    

    protected <T extends Entity> EntitySpec<T> buildSpec(ManagementContext mgmt, Class<T> type, AbstractResource template) {
        return buildSpec(mgmt, type, null, template);
    }
    
    protected <T extends Entity> EntitySpec<T> buildSpec(ManagementContext mgmt, Class<T> type, Class<? extends T> impl, AbstractResource template) {
        Map<String, Object> customAttrs = MutableMap.copyOf(template.getCustomAttributes());
        String name = template.getName();
        String id = template.getId();
        String planId = (String) customAttrs.remove("planId");
        List<Location> childLocations = new BrooklynYamlLocationResolver(mgmt).resolveLocations((Map<String, Object>)customAttrs, true);

        EntitySpec<T> spec = EntitySpec.create(type);
        if (impl != null) spec.impl(impl);
        if (!Strings.isBlank(name))
            spec.displayName(name);
        if (id != null)
            spec.configure(BrooklynCampConstants.TEMPLATE_ID, id);
        if (planId != null)
            spec.configure(BrooklynCampConstants.PLAN_ID, planId);
        if (childLocations != null)
            spec.locations(childLocations);
        
        spec.policySpecs(buildPolicySpecs(mgmt, customAttrs.remove("brooklyn.policies")));
        spec.enricherSpecs(buildEnricherSpecs(mgmt, customAttrs.remove("brooklyn.enrichers")));
        spec.configure(buildEntityConfig(customAttrs));
        
        return spec;
    }

    @SuppressWarnings("unchecked")
    protected Map<Object,Object> buildEntityConfig(Map<?, ?> config) {
        if (config==null) 
            return ImmutableMap.of();
        Map<Object, Object> orig = (Map<Object, Object>)config.get("brooklyn.config");
        Map<Object, Object> result = Maps.newLinkedHashMap();
        if (orig != null) {
            for (Map.Entry<?, ?> entry : orig.entrySet()) {
                Object key = entry.getKey();
                if (key instanceof ConfigKey)
                    result.put((ConfigKey)key, entry.getValue());
                else if (key instanceof HasConfigKey)
                    result.put((HasConfigKey)key, entry.getValue());
                else
                    result.put(ConfigKeys.newConfigKey(Object.class, key.toString()), entry.getValue());
            }
        }
        return result;
    }

    /*
     * recursive method that finds child entities referenced in the config map, side-effecting the entities and entitySpecs maps.
     */
    protected void buildEntityHierarchy(ManagementContext mgmt, Map<Entity, EntitySpec<?>> entitySpecs, Entity parent, List<Map<String, Object>> childConfig) {
        if (childConfig != null) {
            BrooklynCatalog catalog = mgmt.getCatalog();
            for (Map<String, Object> childAttrs : childConfig) {
                MutableMap<String, Object> childAttrsMutable = MutableMap.copyOf(childAttrs);
                String bTypeName = Strings.removeFromStart((String)childAttrsMutable.remove("serviceType"), "brooklyn:");
                final Class<? extends Entity> bType = loadEntityType(catalog, bTypeName);

                final EntitySpec<? extends Entity> spec = buildSpec(mgmt, bType, childAttrsMutable);
                spec.parent(parent);
                Entity entity = newEntity(mgmt, spec);
                entitySpecs.put(entity, spec);

                buildEntityHierarchy(mgmt, entitySpecs, entity, (List<Map<String, Object>>) childAttrs.get("brooklyn.children"));
            }
        }
    }
    
    protected Application createApplicationFromNonCatalogCampTemplate(AssemblyTemplate template, CampPlatform platform) {
        // AssemblyTemplates created via PDP, _specifying_ then entities to put in
        final ManagementContext mgmt = getBrooklynManagementContext(platform);
        final BrooklynCatalog catalog = mgmt.getCatalog();

        Map<Entity, EntitySpec<?>> entitySpecs = Maps.newLinkedHashMap();
        
        EntitySpec<StartableApplication> appSpec = buildSpec(mgmt, StartableApplication.class, BasicApplicationImpl.class, template);
        String planId = (String) template.getCustomAttributes().get("id");
        if (planId != null)
            appSpec.configure(BrooklynCampConstants.PLAN_ID, planId);
        Application app = newEntity(mgmt, appSpec);
        entitySpecs.put(app, appSpec);
        
        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            final PlatformComponentTemplate appChildComponentTemplate = ctl.resolve();
            final String bTypeName = Strings.removeFromStart(appChildComponentTemplate.getType(), "brooklyn:");
            final Class<? extends Entity> bType = loadEntityType(catalog, bTypeName);

            final EntitySpec<? extends Entity> spec = buildSpec(mgmt, bType, appChildComponentTemplate);
            spec.parent(app);
            Entity entity = newEntity(mgmt, spec);
            entitySpecs.put(entity, spec);
            
            List<Map<String, Object>> childAttrs = (List<Map<String, Object>>) appChildComponentTemplate.getCustomAttributes().get("brooklyn.children");
            buildEntityHierarchy(mgmt, entitySpecs, entity, childAttrs);
        }

        for (final Entity entity : entitySpecs.keySet()) {
            final EntitySpec<?> spec = entitySpecs.get(entity);
            
            ((EntityInternal) entity).getExecutionContext().submit(MutableMap.of(), new Runnable() {
                @Override
                public void run() {
                    initEntity(mgmt, entity, (EntitySpec)spec);
                }
            }).getUnchecked();
        }
        
        log.info("REST placing '{}' under management", appSpec);
        Entities.startManagement(app, mgmt);

        return app;
    }
    
    private <T extends Policy> PolicySpec<?> toCorePolicySpec(Class<T> clazz, Map<?, ?> config) {
        Map<?, ?> policyConfig = (config == null) ? Maps.<Object, Object>newLinkedHashMap() : Maps.newLinkedHashMap(config);
        PolicySpec<?> result;
        result = PolicySpec.create(clazz)
                .configure(policyConfig);
        return result;
    }

    private <T extends Enricher> EnricherSpec<?> toCoreEnricherSpec(Class<T> clazz, Map<?, ?> config) {
        Map<?, ?> enricherConfig = (config == null) ? Maps.<Object, Object>newLinkedHashMap() : Maps.newLinkedHashMap(config);
        EnricherSpec<?> result;
        result = EnricherSpec.create(clazz)
                .configure(enricherConfig);
        return result;
    }
    
    private List<PolicySpec<?>> buildPolicySpecs(ManagementContext mgmt, Object policies) {
        List<PolicySpec<?>> policySpecs = new ArrayList<PolicySpec<? extends Policy>>(); 
        if (policies instanceof Iterable) {
            for (Object policy : (Iterable<Object>)policies) {
                if (policy instanceof Map) {
                    String policyTypeName = ((Map<?, ?>) policy).get("policyType").toString();
                    Class<? extends Policy> policyType = null;
                    try {
                        policyType = (Class<? extends Policy>) loadClass(mgmt.getCatalog(), policyTypeName);
                    } catch (ClassNotFoundException e) {
                        throw Exceptions.propagate(e);
                    }
                    policySpecs.add(toCorePolicySpec(policyType, (Map<?, ?>) ((Map<?, ?>) policy).get("brooklyn.config")));
                } else {
                    throw new IllegalArgumentException("policy should be map, not " + policy.getClass());
                }
            }
        } else if (policies != null) {
            // TODO "map" short form
            throw new IllegalArgumentException("policies body should be iterable, not " + policies.getClass());
        }
        return policySpecs;
    }
    
    private List<EnricherSpec<?>> buildEnricherSpecs(ManagementContext mgmt, Object enrichers) {
        List<EnricherSpec<?>> enricherSpecs = Lists.newArrayList();
        if (enrichers instanceof Iterable) {
            for (Object enricher : (Iterable<Object>)enrichers) {
                if (enricher instanceof Map) {
                    String enricherTypeName = ((Map<?, ?>) enricher).get("enricherType").toString();
                    Class<? extends Enricher> enricherType = null;
                    try {
                        enricherType = (Class<? extends Enricher>) loadClass(mgmt.getCatalog(), enricherTypeName);
                    } catch (ClassNotFoundException e) {
                        throw Exceptions.propagate(e);
                    }
                    enricherSpecs.add(toCoreEnricherSpec(enricherType, (Map<?, ?>) ((Map<?, ?>) enricher).get("brooklyn.config")));
                } else {
                    throw new IllegalArgumentException("enricher should be map, not " + enricher.getClass());
                }
            }
        } else if (enrichers != null) {
            throw new IllegalArgumentException("enrichers body should be iterable, not " + enrichers.getClass());
        }
        return enricherSpecs;
    }
}
