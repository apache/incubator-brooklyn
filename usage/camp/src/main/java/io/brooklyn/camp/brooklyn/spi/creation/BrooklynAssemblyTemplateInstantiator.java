package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.spi.platform.HasBrooklynManagementContext;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicApplicationImpl;
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
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.collect.Maps;

public class BrooklynAssemblyTemplateInstantiator implements AssemblyTemplateInstantiator {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAssemblyTemplateInstantiator.class);
    
    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        Application app = create(template, platform);
        Task<?> task = start(app, platform);
        log.info("CAMP created "+app+"; starting in "+task);
        return platform.assemblies().get(app.getApplicationId());
    }

    // note: based on BrooklynRestResourceUtils, but modified to not allow child entities (yet)
    // (will want to revise that when building up from a non-brooklyn template)
    public Application create(AssemblyTemplate template, CampPlatform platform) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);
        
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();
        // TODO: item is always null because template.id is a random String, so
        // createApplicationFromCatalog branch below is never taken.  If `id'
        // key is given in blueprint it is available with:
        // Object customId = template.getCustomAttributes().get("id");
        CatalogItem<?,?> item = catalog.getCatalogItem(template.getId());

        if (item==null) {
            return createApplicationFromNonCatalogCampTemplate(template, platform);
        } else {
            return createApplicationFromCatalog(platform, item, template);
        }
    }

    protected Application createApplicationFromCatalog(CampPlatform platform, CatalogItem<?,?> item, AssemblyTemplate template) {
        ManagementContext mgmt = getBrooklynManagementContext(platform);

        if (!template.getApplicationComponentTemplates().isEmpty() ||
                !template.getPlatformComponentTemplates().isEmpty())
            log.warn("CAMP AssemblyTemplate was not empty when creating from catalog spec; ignoring templates declared within it " +
                    "("+template+")");

        // name (and description) -- not prescribed by camp spec (cf discussion with gil)
        String name = template.getName();
                
        String type = item.getJavaType();
        final Application instance;

        // Load the class; first try to use the appropriate catalog item; but then allow anything that is on the classpath
        final Class<? extends Entity> clazz;
        if (Strings.isEmpty(type)) {
            clazz = BasicApplication.class;
        } else {
            clazz = BrooklynEntityClassResolver.resolveEntity(type, mgmt);
        }
        
        try {
            if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor();
                ApplicationBuilder appBuilder = (ApplicationBuilder) constructor.newInstance();
                
                if (!Strings.isEmpty(name)) appBuilder.appDisplayName(name);
        
                // TODO use resolver's configureEntitySpec instead
                final Map<?,?> configO = (Map<?,?>) template.getCustomAttributes().get("brooklyn.config");

                log.info("CAMP placing '{}' under management", appBuilder);
                appBuilder.configure( convertFlagsToKeys(appBuilder.getType(), configO) );
                instance = appBuilder.manage(mgmt);
                
                List<Location> locations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(template.getCustomAttributes(), false);
                if (locations!=null)
                    ((EntityInternal)instance).addLocations(locations);
                
            } else if (Application.class.isAssignableFrom(clazz)) {
                // TODO use resolver's configureEntitySpec instead
                final Map<?,?> configO = (Map<?,?>) template.getCustomAttributes().get("brooklyn.config");
                
                brooklyn.entity.proxying.EntitySpec<?> coreSpec = toCoreEntitySpec(clazz, name, configO);
                instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);

                log.info("CAMP placing '{}' under management", instance);
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

    private ManagementContext getBrooklynManagementContext(CampPlatform platform) {
        return ((HasBrooklynManagementContext)platform).getBrooklynManagementContext();
    }
    
    public Task<?> start(Application app, CampPlatform platform) {
        return Entities.invokeEffector((EntityLocal)app, app, Startable.START,
            // locations already set in the entities themselves;
            // TODO make it so that this arg does not have to be supplied to START !
            MutableMap.of("locations", MutableList.of()));
    }

    // TODO this is an exact copy of BrooklynRestResoureUtils; make available to both somehow? (or even better, avoid somehow)
    private static Map<?,?> convertFlagsToKeys(Class<? extends Entity> javaType, Map<?, ?> config) {
        if (config==null || config.isEmpty() || javaType==null) return config;

        Map<String, ConfigKey<?>> configKeys = EntityTypes.getDefinedConfigKeys(javaType);
        Map<Object,Object> result = new LinkedHashMap<Object,Object>();
        for (Map.Entry<?,?> entry: config.entrySet()) {
            log.debug("Setting key {} to {} for CAMP creation of {}", new Object[] { entry.getKey(), entry.getValue(), javaType});
            Object key = configKeys.get(entry.getKey());
            if (key==null) {
                log.warn("Unrecognised config key {} passed to {}; will be treated as flag (and likely ignored)", entry.getKey(), javaType);
                key = entry.getKey();
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    // TODO exact copy of BRRU, as above
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends Entity> EntitySpec<?> toCoreEntitySpec(Class<T> clazz, String name, Map<?,?> configO) {
        Map<?, ?> config = (configO == null) ? Maps.newLinkedHashMap() : Maps.newLinkedHashMap(configO);
        
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

    /*
     * recursive method that finds child entities referenced in the config map, side-effecting the entities and entitySpecs maps.
     */
    protected void buildEntityHierarchy(ManagementContext mgmt, Map<Entity, EntitySpec<?>> entitySpecs, Entity parent, List<Map<String, Object>> childConfig) {
        if (childConfig != null) {
            for (Map<String, Object> childAttrs : childConfig) {
                BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, childAttrs);
                EntitySpec<? extends Entity> spec = entityResolver.resolveSpec();

                spec.parent(parent);
                Entity entity = entityResolver.newEntity(spec);
                entitySpecs.put(entity, spec);
                buildEntityHierarchy(mgmt, entitySpecs, entity, entityResolver.getChildren(childAttrs));
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    protected Application createApplicationFromNonCatalogCampTemplate(AssemblyTemplate template, CampPlatform platform) {
        // AssemblyTemplates created via PDP, _specifying_ then entities to put in
        final ManagementContext mgmt = getBrooklynManagementContext(platform);

        Map<Entity, EntitySpec<?>> rootEntities = Maps.newLinkedHashMap();
        Map<Entity, EntitySpec<?>> allEntities = Maps.newLinkedHashMap();
        buildEntities(template, rootEntities, allEntities, mgmt);
        
        EntitySpec<StartableApplication> appSpec;
        StartableApplication app;
        if(shouldWrapInApp(template, rootEntities)) {
            BrooklynComponentTemplateResolver appResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, template);
            appSpec = appResolver.resolveSpec(StartableApplication.class, BasicApplicationImpl.class);
            app = appResolver.newEntity(appSpec);
            setEntitiesParent(rootEntities, app);
            allEntities.put(app, appSpec);
        } else {
            Entry<Entity, EntitySpec<?>> entry = rootEntities.entrySet().iterator().next();
            app = (StartableApplication)entry.getKey();
            appSpec = (EntitySpec<StartableApplication>)entry.getValue();
        }
        
        initEntities(mgmt, allEntities);
        
        log.info("CAMP placing '{}' under management", appSpec);
        Entities.startManagement(app, mgmt);

        return app;
    }

    private void setEntitiesParent(Map<Entity, EntitySpec<?>> entities, Application parentApp) {
        for(Entry<Entity, EntitySpec<?>> entry : entities.entrySet()) {
            entry.getValue().parent(parentApp);
            entry.getKey().setParent(parentApp);
        }
    }

    private void initEntities(final ManagementContext mgmt, Map<Entity, EntitySpec<?>> entities) {
        for (Entry<Entity, EntitySpec<?>> entry : entities.entrySet()) {
            final Entity entity = entry.getKey();
            
            @SuppressWarnings("unchecked")
            final EntitySpec<Entity> spec = (EntitySpec<Entity>)entry.getValue();
            
            ((EntityInternal) entity).getExecutionContext().submit(MutableMap.of(), new Runnable() {
                @Override
                public void run() {
                    initEntity(mgmt, entity, spec);
                }
            }).getUnchecked();
        }
    }

    private boolean shouldWrapInApp(AssemblyTemplate template, Map<Entity, EntitySpec<?>> rootEntities) {
        return isWrapAppRequested(template) ||
                rootEntities.size() != 1 ||
                !(rootEntities.keySet().iterator().next() instanceof StartableApplication);
    }

    private boolean isWrapAppRequested(AssemblyTemplate template) {
        return Boolean.TRUE.equals(template.getCustomAttributes().get("wrappedApp"));
    }

    private void buildEntities(AssemblyTemplate template, Map<Entity, EntitySpec<?>> parentEntities, 
            Map<Entity, EntitySpec<?>> allEntities, ManagementContext mgmt) {
        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            PlatformComponentTemplate appChildComponentTemplate = ctl.resolve();
            BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, appChildComponentTemplate);
            EntitySpec<? extends Entity> spec = entityResolver.resolveSpec();
            Entity entity = entityResolver.newEntity(spec);
            parentEntities.put(entity, spec);
            allEntities.put(entity, spec);
            buildEntityHierarchy(mgmt, allEntities, entity, entityResolver.getChildren(appChildComponentTemplate.getCustomAttributes()));
        }
    }

    protected <T extends Entity> void initEntity(ManagementContext mgmt, T entity, EntitySpec<T> spec) {
        InternalEntityFactory entityFactory = ((ManagementContextInternal)mgmt).getEntityFactory();
        entityFactory.initEntity(entity, spec);
    }

}
