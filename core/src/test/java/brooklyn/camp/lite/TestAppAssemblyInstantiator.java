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
package brooklyn.camp.lite;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.BasicAssemblyTemplateInstantiator;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;

/** simple illustrative instantiator which always makes a {@link TestApplication}, populated with {@link TestEntity} children,
 * all setting {@link TestEntity#CONF_NAME} for the name in the plan and in the service specs
 * <p>
 * the "real" instantiator for brooklyn is in brooklyn-camp project, not visible here, so let's have something we can test */
public class TestAppAssemblyInstantiator extends BasicAssemblyTemplateInstantiator implements AssemblyTemplateSpecInstantiator {

    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        if (!(platform instanceof HasBrooklynManagementContext)) {
            throw new IllegalStateException("Instantiator can only be used with CAMP platforms with a Brooklyn management context");
        }
        ManagementContext mgmt = ((HasBrooklynManagementContext)platform).getBrooklynManagementContext();
        
        TestApplication app = (TestApplication) mgmt.getEntityManager().createEntity( createSpec(template, platform, null, false) );
        mgmt.getEntityManager().manage(app);

        return new TestAppAssembly(app);
    }

    @Override
    public EntitySpec<?> createSpec(AssemblyTemplate template, CampPlatform platform, BrooklynClassLoadingContext loader, boolean autoUnwrap) {
        EntitySpec<TestApplication> app = EntitySpec.create(TestApplication.class)
            .configure(TestEntity.CONF_NAME, template.getName())
            .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", template.getType(), "desc", template.getDescription()));
        applyBrooklynConfig(template, app);
        
        for (ResolvableLink<PlatformComponentTemplate> t: template.getPlatformComponentTemplates().links()) {
            EntitySpec<TestEntity> spec = EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, t.getName())
                .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", t.resolve().getType(), "desc", t.resolve().getDescription()));
            applyBrooklynConfig(t.resolve(), app);
            app.child(spec);
        }
        
        return app;
    }

    @SuppressWarnings("rawtypes")
    private void applyBrooklynConfig(AbstractResource template, EntitySpec<TestApplication> app) {
        Object bc = template.getCustomAttributes().get("brooklyn.config");
        if (bc instanceof Map)
            app.configure(ConfigBag.newInstance().putAll((Map)bc).getAllConfigAsConfigKeyMap());
    }

    @Override
    public EntitySpec<?> createNestedSpec(AssemblyTemplate template, CampPlatform platform, BrooklynClassLoadingContext itemLoader, Set<String> encounteredCatalogTypes) {
        return createSpec(template, platform, itemLoader, true);
    }

}
