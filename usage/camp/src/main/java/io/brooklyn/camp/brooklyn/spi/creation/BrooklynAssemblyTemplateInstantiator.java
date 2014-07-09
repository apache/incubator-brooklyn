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
import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate.Builder;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class BrooklynAssemblyTemplateInstantiator implements AssemblyTemplateSpecInstantiator {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAssemblyTemplateInstantiator.class);
    
    public static final String NEVER_UNWRAP_APPS_PROPERTY = "wrappedApp";
    
    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        Application app = create(template, platform);
        Task<?> task = start(app, platform);
        log.info("CAMP created "+app+"; starting in "+task);
        return platform.assemblies().get(app.getApplicationId());
    }

    public EntitySpec<?> createSpec(AssemblyTemplate template, CampPlatform platform) {
        return createAppOrSpec(template, platform, true).getSpec();
    }

    // note: based on BrooklynRestResourceUtils, but modified to not allow child entities (yet)
    // (will want to revise that when building up from a non-brooklyn template)
    public Application create(AssemblyTemplate template, CampPlatform platform) {
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        
        AppOrSpec appOrSpec = createAppOrSpec(template, platform, false);
        
        if (appOrSpec.hasApp())
            return appOrSpec.getApp();
        if (!appOrSpec.hasSpec())
            throw new IllegalStateException("No spec could be produced from "+template);
        
        EntitySpec<? extends Application> spec = appOrSpec.getSpec();
        
        Application instance = (Application) mgmt.getEntityManager().createEntity(spec);
        log.info("CAMP placing '{}' under management", instance);
        Entities.startManagement(instance, mgmt);

        return instance;
    }
    
    protected AppOrSpec createAppOrSpec(AssemblyTemplate template, CampPlatform platform, boolean requireSpec) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);
        
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();
        
        CatalogItem<?,?> item = catalog.getCatalogItem(template.getName());
        BrooklynClassLoadingContext loader;
        if (item!=null) {
            loader = item.newClassLoadingContext(mgmt);
        } else {
            loader = JavaBrooklynClassLoadingContext.newDefault(mgmt);
        }
        return new AppOrSpec(createApplicationFromCampTemplate(template, platform, loader));
    }
    
    private static class AppOrSpec {
        private final Application app;
        private final EntitySpec<? extends Application> spec;
        
        public AppOrSpec(Application app) {
            this.app = app;
            this.spec = null;
        }

        public AppOrSpec(EntitySpec<? extends Application> spec) {
            this.app = null;
            this.spec = spec;
        }

        public boolean hasApp() {
            return app!=null;
        }
        public boolean hasSpec() {
            return spec!=null;
        }
        public Application getApp() {
            return app;
        }
        public EntitySpec<? extends Application> getSpec() {
            return spec;
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

    @SuppressWarnings("unchecked")
    protected EntitySpec<? extends Application> createApplicationFromCampTemplate(AssemblyTemplate template, CampPlatform platform, BrooklynClassLoadingContext loader) {
        // AssemblyTemplates created via PDP, _specifying_ then entities to put in

        BrooklynComponentTemplateResolver resolver = BrooklynComponentTemplateResolver.Factory.newInstance(
            loader, buildWrapperAppTemplate(template));
        EntitySpec<? extends Application> app = resolver.resolveSpec();
        
        // first build the children into an empty shell app
        List<EntitySpec<?>> childSpecs = buildTemplateServicesAsSpecs(loader, template, platform);
        for (EntitySpec<?> childSpec : childSpecs) {
            app.child(childSpec);
        }
        
        if (shouldUnwrap(template, app)) {
            EntitySpec<? extends Application> oldApp = app;
            app = (EntitySpec<? extends Application>) Iterables.getOnlyElement( app.getChildren() );
            // if promoted, apply the transformations done to the app
            // (normally this will be done by the resolveSpec call above)
            if (app.getDisplayName()==null) app.displayName(oldApp.getDisplayName());
            app.locations(oldApp.getLocations());
        }
        
        return app;
    }

    private AssemblyTemplate buildWrapperAppTemplate(AssemblyTemplate template) {
        Builder<? extends AssemblyTemplate> builder = AssemblyTemplate.builder();
        builder.type("brooklyn:" + BasicApplicationImpl.class.getName());
        builder.id(template.getId());
        builder.name(template.getName());
        for (Entry<String, Object> entry : template.getCustomAttributes().entrySet()) {
            builder.customAttribute(entry.getKey(), entry.getValue());
        }
        builder.instantiator(template.getInstantiator());
        AssemblyTemplate wrapTemplate = builder.build();
        return wrapTemplate;
    }

    protected boolean shouldUnwrap(AssemblyTemplate template, EntitySpec<? extends Application> app) {
        Object leaveWrapped = template.getCustomAttributes().get(NEVER_UNWRAP_APPS_PROPERTY);
        if (leaveWrapped!=null) {
            if (TypeCoercions.coerce(leaveWrapped, Boolean.class))
                return false;
        }
        
        if (app.getChildren().size()!=1) 
            return false;
        
        EntitySpec<?> childSpec = Iterables.getOnlyElement(app.getChildren());
        if (childSpec.getType()==null || !Application.class.isAssignableFrom(childSpec.getType()))
            return false;

        Set<String> rootAttrs = template.getCustomAttributes().keySet();
        for (String rootAttr: rootAttrs) {
            if (rootAttr.equals("brooklyn.catalog")) {
                // this attr does not block promotion
                continue;
            }
            if (rootAttr.startsWith("brooklyn.")) {
                // any others in 'brooklyn' namespace will block promotion
                return false;
            }
            // location is allowed in both, and is copied on promotion
            // (name also copied)
            // others are root currently are ignored on promotion; they are usually metadata
            // TODO might be nice to know what we are excluding
        }
        
        return true;
    }

    private List<EntitySpec<?>> buildTemplateServicesAsSpecs(BrooklynClassLoadingContext loader, AssemblyTemplate template, CampPlatform platform) {
        return buildTemplateServicesAsSpecsImpl(loader, template, platform, Sets.<String>newLinkedHashSet());
    }

    private List<EntitySpec<?>> buildTemplateServicesAsSpecsImpl(BrooklynClassLoadingContext loader, AssemblyTemplate template, CampPlatform platform, Set<String> encounteredCatalogTypes) {
        List<EntitySpec<?>> result = Lists.newArrayList();
        
        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            PlatformComponentTemplate appChildComponentTemplate = ctl.resolve();
            BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(loader, appChildComponentTemplate);
            ManagementContext mgmt = loader.getManagementContext();

            String catalogIdOrJavaType = entityResolver.getCatalogIdOrJavaType();
            CatalogItem<Entity, EntitySpec<?>> item = entityResolver.getCatalogItem();

            boolean firstOccurrence = encounteredCatalogTypes.add(catalogIdOrJavaType);
            boolean recursiveButTryJava = !firstOccurrence;
                         
            if (log.isTraceEnabled()) log.trace("Building CAMP template services: type="+catalogIdOrJavaType+"; item="+item+"; loader="+loader+"; template="+template+"; encounteredCatalogTypes="+encounteredCatalogTypes);

            EntitySpec<?> spec;
            if (item == null || item.getJavaType() != null || entityResolver.isJavaTypePrefix()) {
                spec = entityResolver.resolveSpec();
            } else if (recursiveButTryJava) {
                if (entityResolver.tryLoadEntityClass().isAbsent()) {
                    throw new IllegalStateException("Recursive reference to " + catalogIdOrJavaType + " (and cannot be resolved as a Java type)");
                }
                spec = entityResolver.resolveSpec();
            } else {
                spec = resolveCatalogYamlReferenceSpec(platform, mgmt, item, encounteredCatalogTypes);
            }

            BrooklynClassLoadingContext newLoader = entityResolver.loader;
            buildChildrenEntitySpecs(newLoader, spec, entityResolver.getChildren(appChildComponentTemplate.getCustomAttributes()));
            
            result.add(spec);
        }
        return result;
    }

    private EntitySpec<?> resolveCatalogYamlReferenceSpec(CampPlatform platform,
            ManagementContext mgmt,
            CatalogItem<Entity, EntitySpec<?>> item,
            Set<String> encounteredCatalogTypes) {
        
        String yaml = item.getPlanYaml();
        Reader input = new StringReader(yaml);
        
        AssemblyTemplate at;
        BrooklynClassLoadingContext itemLoader = item.newClassLoadingContext(mgmt);
        BrooklynLoaderTracker.setLoader(itemLoader);
        try {
            at = platform.pdp().registerDeploymentPlan(input);
        } finally {
            BrooklynLoaderTracker.unsetLoader(itemLoader);
        }

        // In case we want to allow multiple top-level entities in a catalog we need to think
        // about what it would mean to subsequently call buildChildrenEntitySpecs on the list of top-level entities!
        try {
            AssemblyTemplateInstantiator ati = at.getInstantiator().newInstance();
            if (ati instanceof BrooklynAssemblyTemplateInstantiator) {
                List<EntitySpec<?>> specs = ((BrooklynAssemblyTemplateInstantiator)ati).buildTemplateServicesAsSpecsImpl(itemLoader, at, platform, encounteredCatalogTypes);
                if (specs.size() > 1) {
                    throw new UnsupportedOperationException("Only supporting single service in catalog item currently: got "+specs);
                }
                return specs.get(0);
            } else {
                throw new IllegalStateException("Cannot create application with instantiator: " + ati);
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    protected void buildChildrenEntitySpecs(BrooklynClassLoadingContext loader, EntitySpec<?> parent, List<Map<String, Object>> childConfig) {
        if (childConfig != null) {
            for (Map<String, Object> childAttrs : childConfig) {
                BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(loader, childAttrs);
                EntitySpec<? extends Entity> spec = entityResolver.resolveSpec();
                parent.child(spec);

                // get the new loader in case the OSGi bundles from parent were added;
                // not so important now but if we start working with versions this may be important
                BrooklynClassLoadingContext newLoader = entityResolver.loader;
                buildChildrenEntitySpecs(newLoader, spec, entityResolver.getChildren(childAttrs));
            }
        }
    }
}
