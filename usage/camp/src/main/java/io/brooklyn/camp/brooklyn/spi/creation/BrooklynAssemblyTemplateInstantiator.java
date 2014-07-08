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
package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
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

public class BrooklynAssemblyTemplateInstantiator implements AssemblyTemplateSpecInstantiator {

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

    public EntitySpec<?> createSpec(AssemblyTemplate template, CampPlatform platform) {
        // TODO rewrite this class so everything below returns a spec, then rewrite above just to instantiate (and start?) the spec
        throw new UnsupportedOperationException();
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
                
                applyLocations(mgmt, template, instance);
                
            } else if (Application.class.isAssignableFrom(clazz)) {
                // TODO use resolver's configureEntitySpec instead
                final Map<?,?> configO = (Map<?,?>) template.getCustomAttributes().get("brooklyn.config");
                
                brooklyn.entity.proxying.EntitySpec<?> coreSpec = toCoreEntitySpec(clazz, name, configO);
                instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);

                log.info("CAMP placing '{}' under management", instance);
                Entities.startManagement(instance, mgmt);
                
                applyLocations(mgmt, template, instance);
                
            } else {
                throw new IllegalArgumentException("Class "+clazz+" must extend one of ApplicationBuilder or Application");
            }
            
            return instance;
            
        } catch (Exception e) {
            log.error("CAMP failed to create application: "+e, e);
            throw Exceptions.propagate(e);
        }
    }

    private void applyLocations(ManagementContext mgmt, AssemblyTemplate template, final Application instance) {
        List<Location> locations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(template.getCustomAttributes(), false);
        if (locations!=null)
            ((EntityInternal)instance).addLocations(locations);
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
    
    protected Application createApplicationFromNonCatalogCampTemplate(AssemblyTemplate template, CampPlatform platform) {
        // AssemblyTemplates created via PDP, _specifying_ then entities to put in
        final ManagementContext mgmt = getBrooklynManagementContext(platform);

        Map<Entity, EntitySpec<?>> allEntities = Maps.newLinkedHashMap();
        StartableApplication rootApp = buildRootApp(template, platform, allEntities);
        initEntities(mgmt, allEntities);
        log.info("CAMP placing '{}' under management", allEntities.get(rootApp));
        Entities.startManagement(rootApp, mgmt);
        return rootApp;
    }

    private StartableApplication buildRootApp(AssemblyTemplate template, CampPlatform platform, Map<Entity, EntitySpec<?>> allEntities) {
        if (shouldWrapInApp(template, platform)) {
            return buildWrappedApp(template, platform, allEntities);
        } else {
            return buildPromotedApp(template, platform, allEntities);
        }
    }

    private StartableApplication buildWrappedApp(AssemblyTemplate template, CampPlatform platform, Map<Entity, EntitySpec<?>> allEntities) {
        final ManagementContext mgmt = getBrooklynManagementContext(platform);
        
        BrooklynComponentTemplateResolver appResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, template);
        EntitySpec<StartableApplication> wrapAppSpec = appResolver.resolveSpec(StartableApplication.class, BasicApplicationImpl.class);
        StartableApplication wrapApp = appResolver.newEntity(wrapAppSpec);
        allEntities.put(wrapApp, wrapAppSpec);
        
        buildEntities(template, wrapApp, wrapAppSpec, allEntities, mgmt);
        
        return wrapApp;
    }

    private StartableApplication buildPromotedApp(AssemblyTemplate template, CampPlatform platform, Map<Entity, EntitySpec<?>> allEntities) {
        final ManagementContext mgmt = getBrooklynManagementContext(platform);
        
        ResolvableLink<PlatformComponentTemplate> promotedAppTemplate = template.getPlatformComponentTemplates().links().get(0);
        
        PlatformComponentTemplate appChildComponentTemplate = promotedAppTemplate.resolve();
        BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, appChildComponentTemplate);
        EntitySpec<?> spec = buildEntitySpecNonHierarchical(null, appChildComponentTemplate, allEntities, mgmt, entityResolver);

        // and this is needed in case 'name' was set at template level (eg ApplicationResourceTest.testDeployApplicationYaml)
        if (spec.getDisplayName()==null && template.getName()!=null)
            spec.displayName(template.getName());
            
        StartableApplication app = (StartableApplication) buildEntityHierarchical(spec, appChildComponentTemplate, allEntities, mgmt, entityResolver);
        
        // TODO i (alex) think we need this because locations defined at the root of the template could have been lost otherwise?
        applyLocations(mgmt, template, app);
        
        return app;
    }

    private void buildEntities(AssemblyTemplate template, StartableApplication app, EntitySpec<StartableApplication> appSpec,
            Map<Entity, EntitySpec<?>> allEntities, ManagementContext mgmt) {
        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            buildEntity(app, ctl, allEntities, mgmt);
        }
    }

    private Entity buildEntity(StartableApplication parent, ResolvableLink<PlatformComponentTemplate> ctl,
            Map<Entity, EntitySpec<?>> allEntities, ManagementContext mgmt) {
        PlatformComponentTemplate appChildComponentTemplate = ctl.resolve();
        BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, appChildComponentTemplate);
        EntitySpec<?> spec = buildEntitySpecNonHierarchical(parent, appChildComponentTemplate, allEntities, mgmt, entityResolver);
        return buildEntityHierarchical(spec, appChildComponentTemplate, allEntities, mgmt, entityResolver);
    }
    private EntitySpec<?> buildEntitySpecNonHierarchical(StartableApplication parent, PlatformComponentTemplate appChildComponentTemplate,
            Map<Entity, EntitySpec<?>> allEntities, ManagementContext mgmt, BrooklynComponentTemplateResolver entityResolver) {
        EntitySpec<? extends Entity> spec = entityResolver.resolveSpec();
        if(parent != null) {
            spec.parent(parent);
        }
        return spec;
    }
    private Entity buildEntityHierarchical(EntitySpec<?> spec, PlatformComponentTemplate appChildComponentTemplate,
            Map<Entity, EntitySpec<?>> allEntities, ManagementContext mgmt, BrooklynComponentTemplateResolver entityResolver) {
        Entity entity = entityResolver.newEntity(spec);
        allEntities.put(entity, spec);
        buildEntityHierarchy(mgmt, allEntities, entity, entityResolver.getChildren(appChildComponentTemplate.getCustomAttributes()));
        return entity;
    }

    private boolean shouldWrapInApp(AssemblyTemplate template, CampPlatform platform) {
        return isWrapAppRequested(template) ||
                !isSingleApp(template, platform);
    }

    private boolean isWrapAppRequested(AssemblyTemplate template) {
        return Boolean.TRUE.equals(template.getCustomAttributes().get("wrappedApp"));
    }

    protected boolean isSingleApp(AssemblyTemplate template, CampPlatform platform) {
        // AssemblyTemplates created via PDP, _specifying_ then entities to put in
        final ManagementContext mgmt = getBrooklynManagementContext(platform);

        List<ResolvableLink<PlatformComponentTemplate>> pct = template.getPlatformComponentTemplates().links();
        if(pct.size() == 1) {
            ResolvableLink<PlatformComponentTemplate> res = pct.get(0);
            PlatformComponentTemplate templ = res.resolve();
            Class<Entity> entity = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, templ).loadEntityClass();
            if(StartableApplication.class.isAssignableFrom(entity)) {
                return true;
            }
        }
        return false;
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

    protected <T extends Entity> void initEntity(ManagementContext mgmt, T entity, EntitySpec<T> spec) {
        InternalEntityFactory entityFactory = ((ManagementContextInternal)mgmt).getEntityFactory();
        entityFactory.initEntity(entity, spec);
    }

}
