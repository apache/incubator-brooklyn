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

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate.Builder;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;

import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;

import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.internal.EntityManagementUtils;
import brooklyn.management.internal.EntityManagementUtils.CreationResult;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.net.Urls;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class BrooklynAssemblyTemplateInstantiator implements AssemblyTemplateSpecInstantiator {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAssemblyTemplateInstantiator.class);

    public static final String NEVER_UNWRAP_APPS_PROPERTY = "wrappedApp";

    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        Application app = create(template, platform);
        CreationResult<Application, Void> start = EntityManagementUtils.start(app);
        log.debug("CAMP created "+app+"; starting in "+start.task());
        return platform.assemblies().get(app.getApplicationId());
    }

    public Application create(AssemblyTemplate template, CampPlatform platform) {
        Application instance = EntityManagementUtils.createUnstarted(getBrooklynManagementContext(platform), template);
        log.debug("CAMP created {}", instance);
        return instance;
    }

    private ManagementContext getBrooklynManagementContext(CampPlatform platform) {
        return ((HasBrooklynManagementContext)platform).getBrooklynManagementContext();
    }

    @SuppressWarnings("unchecked")
    public EntitySpec<? extends Application> createSpec(AssemblyTemplate template, CampPlatform platform, BrooklynClassLoadingContext loader, boolean autoUnwrapIfPossible) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);

        // AssemblyTemplates created via PDP, _specifying_ then entities to put in

        BrooklynComponentTemplateResolver resolver = BrooklynComponentTemplateResolver.Factory.newInstance(
            loader, buildWrapperAppTemplate(template));
        EntitySpec<? extends Application> app = resolver.resolveSpec(null);

        // first build the children into an empty shell app
        List<EntitySpec<?>> childSpecs = buildTemplateServicesAsSpecs(loader, template, platform);
        for (EntitySpec<?> childSpec : childSpecs) {
            app.child(childSpec);
        }

        if (autoUnwrapIfPossible && shouldUnwrap(template, app)) {
            EntitySpec<? extends Application> oldApp = app;
            app = (EntitySpec<? extends Application>) Iterables.getOnlyElement( app.getChildren() );

            // if promoted, apply the transformations done to the app
            // (transformations will be done by the resolveSpec call above, but we are collapsing oldApp so transfer to app=newApp)
            EntityManagementUtils.collapseSpec(oldApp, app);
        } else {
            app.configure(EntityManagementUtils.WRAPPER_APP_MARKER, Boolean.TRUE);
        }

        return app;
    }

    private AssemblyTemplate buildWrapperAppTemplate(AssemblyTemplate template) {
        Builder<? extends AssemblyTemplate> builder = AssemblyTemplate.builder();
        builder.type("brooklyn:" + BasicApplicationImpl.class.getName());
        builder.id(template.getId());
        builder.name(template.getName());
        builder.sourceCode(template.getSourceCode());
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

        return EntityManagementUtils.hasNoNameOrCustomKeysOrRoot(template, app);
    }

    private List<EntitySpec<?>> buildTemplateServicesAsSpecs(BrooklynClassLoadingContext loader, AssemblyTemplate template, CampPlatform platform) {
        return buildTemplateServicesAsSpecsImpl(loader, template, platform, Sets.<String>newLinkedHashSet());
    }

    private List<EntitySpec<?>> buildTemplateServicesAsSpecsImpl(BrooklynClassLoadingContext loader, AssemblyTemplate template, CampPlatform platform, Set<String> encounteredCatalogTypes) {
        List<EntitySpec<?>> result = Lists.newArrayList();

        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            PlatformComponentTemplate appChildComponentTemplate = ctl.resolve();
            BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(loader, appChildComponentTemplate);
            EntitySpec<?> spec = resolveSpec(ResourceUtils.create(this), entityResolver, encounteredCatalogTypes);

            result.add(spec);
        }
        return result;
    }

    static EntitySpec<?> resolveSpec(ResourceUtils ru,
            BrooklynComponentTemplateResolver entityResolver,
            Set<String> encounteredCatalogTypes) {
        String brooklynType = entityResolver.getServiceTypeResolver().getBrooklynType(entityResolver.getDeclaredType());
        CatalogItem<Entity, EntitySpec<?>> item = entityResolver.getServiceTypeResolver().getCatalogItem(entityResolver, entityResolver.getDeclaredType());

        if (log.isTraceEnabled()) log.trace("Building CAMP template services: type="+brooklynType+"; item="+item+"; loader="+entityResolver.getLoader()+"; encounteredCatalogTypes="+encounteredCatalogTypes);

        EntitySpec<?> spec = null;
        String protocol = Urls.getProtocol(brooklynType);
        if (protocol != null) {
            if (BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST.contains(protocol)) {
                spec = tryResolveYamlUrlReferenceSpec(ru, brooklynType, entityResolver.getLoader(), encounteredCatalogTypes);
                if (spec != null) {
                    entityResolver.populateSpec(spec);
                }
            } else {
                // TODO support https above
                // TODO this will probably be logged if we refer to  chef:cookbook  or other service types which BCTR accepts;
                // better would be to have BCTR supporting the calls above
                log.debug("The reference " + brooklynType + " looks like a URL (running the CAMP Brooklyn assembly-template instantiator) but the protocol " +
                        protocol + " isn't white listed (" + BrooklynCampConstants.YAML_URL_PROTOCOL_WHITELIST + "). " +
                        "Will try to load it as catalog item or java type.");
            }
        }

        if (spec == null) {
            // load from java or yaml
            spec = entityResolver.resolveSpec(encounteredCatalogTypes);
        }

        return spec;
    }

    private static EntitySpec<?> tryResolveYamlUrlReferenceSpec(
            ResourceUtils ru,
            String brooklynType, BrooklynClassLoadingContext itemLoader,
            Set<String> encounteredCatalogTypes) {
        ManagementContext mgmt = itemLoader.getManagementContext();
        Reader yaml;
        try {
            yaml = new InputStreamReader(ru.getResourceFromUrl(brooklynType), "UTF-8");
        } catch (Exception e) {
            log.warn("AssemblyTemplate type " + brooklynType + " which looks like a URL can't be fetched.", e);
            return null;
        }
        try {
            return createNestedSpec(mgmt, encounteredCatalogTypes, yaml, itemLoader);
        } finally {
            try {
                yaml.close();
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            }
        }
    }

    static EntitySpec<?> resolveCatalogYamlReferenceSpec(
            ManagementContext mgmt,
            CatalogItem<Entity, EntitySpec<?>> item,
            Set<String> encounteredCatalogTypes) {

        String yaml = item.getPlanYaml();
        Reader input = new StringReader(yaml);
        BrooklynClassLoadingContext itemLoader = CatalogUtils.newClassLoadingContext(mgmt, item);

        return createNestedSpec(mgmt, encounteredCatalogTypes, input, itemLoader);
    }

    private static EntitySpec<?> createNestedSpec(ManagementContext mgmt,
            Set<String> encounteredCatalogTypes, Reader input,
            BrooklynClassLoadingContext itemLoader) {
        CampPlatform platform = BrooklynServerConfig.getCampPlatform(mgmt).get();

        AssemblyTemplate at;
        BrooklynLoaderTracker.setLoader(itemLoader);
        try {
            at = platform.pdp().registerDeploymentPlan(input);
        } finally {
            BrooklynLoaderTracker.unsetLoader(itemLoader);
        }
        return createNestedSpecStatic(at, platform, itemLoader, encounteredCatalogTypes);
    }

    @Override
    public EntitySpec<?> createNestedSpec(
            AssemblyTemplate template,
            CampPlatform platform,
            BrooklynClassLoadingContext itemLoader,
            Set<String> encounteredCatalogTypes) {
        return createNestedSpecStatic(template, platform, itemLoader, encounteredCatalogTypes);
    }
    
    private static EntitySpec<?> createNestedSpecStatic(
        AssemblyTemplate template,
        CampPlatform platform,
        BrooklynClassLoadingContext itemLoader,
        Set<String> encounteredCatalogTypes) {
        // In case we want to allow multiple top-level entities in a catalog we need to think
        // about what it would mean to subsequently call buildChildrenEntitySpecs on the list of top-level entities!
        try {
            AssemblyTemplateInstantiator ati = template.getInstantiator().newInstance();
            if (ati instanceof BrooklynAssemblyTemplateInstantiator) {
                List<EntitySpec<?>> specs = ((BrooklynAssemblyTemplateInstantiator)ati).buildTemplateServicesAsSpecsImpl(itemLoader, template, platform, encounteredCatalogTypes);
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

}
