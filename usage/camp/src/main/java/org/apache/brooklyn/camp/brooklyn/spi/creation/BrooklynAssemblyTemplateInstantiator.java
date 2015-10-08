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

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import org.apache.brooklyn.camp.spi.Assembly;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.AssemblyTemplate.Builder;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;
import org.apache.brooklyn.camp.spi.collection.ResolvableLink;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils.CreationResult;
import org.apache.brooklyn.core.mgmt.HasBrooklynManagementContext;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.entity.stock.BasicApplicationImpl;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private Application create(AssemblyTemplate template, CampPlatform platform) {
        ManagementContext mgmt = getManagementContext(platform);
        BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
        EntitySpec<? extends Application> spec = createApplicationSpec(template, platform, loader);
        Application instance = mgmt.getEntityManager().createEntity(spec);
        log.info("CAMP placing '{}' under management", instance);
        Entities.startManagement(instance, mgmt);
        return instance;
    }

    @Override
    public List<EntitySpec<?>> createServiceSpecs(AssemblyTemplate template,
            CampPlatform platform, BrooklynClassLoadingContext itemLoader,
            Set<String> encounteredCatalogTypes) {
        return buildTemplateServicesAsSpecs(itemLoader, template, platform, encounteredCatalogTypes, true);
    }

    @Override
    public EntitySpec<? extends Application> createApplicationSpec(
            AssemblyTemplate template,
            CampPlatform platform,
            BrooklynClassLoadingContext loader) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);

        // AssemblyTemplates created via PDP, _specifying_ then entities to put in

        BrooklynComponentTemplateResolver resolver = BrooklynComponentTemplateResolver.Factory.newInstance(
            loader, buildWrapperAppTemplate(template));
        EntitySpec<? extends Application> app = resolver.resolveSpec(null, false);
        app.configure(EntityManagementUtils.WRAPPER_APP_MARKER, Boolean.TRUE);

        // first build the children into an empty shell app
        List<EntitySpec<?>> childSpecs = createServiceSpecs(template, platform, loader, Sets.<String>newLinkedHashSet());
        for (EntitySpec<?> childSpec : childSpecs) {
            app.child(childSpec);
        }

        if (shouldUnwrap(template, app)) {
            app = EntityManagementUtils.unwrapApplication(app);
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

    private boolean shouldUnwrap(AssemblyTemplate template, EntitySpec<? extends Application> app) {
        if (Boolean.TRUE.equals(TypeCoercions.coerce(template.getCustomAttributes().get(NEVER_UNWRAP_APPS_PROPERTY), Boolean.class)))
            return false;
        return EntityManagementUtils.canPromoteWrappedApplication(app);
    }

    private List<EntitySpec<?>> buildTemplateServicesAsSpecs(BrooklynClassLoadingContext loader, AssemblyTemplate template, CampPlatform platform, Set<String> encounteredCatalogTypes, boolean canUseOtherTransformers) {
        List<EntitySpec<?>> result = Lists.newArrayList();

        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            PlatformComponentTemplate appChildComponentTemplate = ctl.resolve();
            BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(loader, appChildComponentTemplate);
            EntitySpec<?> spec = entityResolver.resolveSpec(encounteredCatalogTypes, canUseOtherTransformers);
            result.add(spec);
        }
        return result;
    }

    private static ManagementContext getManagementContext(CampPlatform platform) {
        return ((HasBrooklynManagementContext)platform).getBrooklynManagementContext();
    }

}
