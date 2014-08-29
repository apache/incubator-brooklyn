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
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.BasicAssemblyTemplateInstantiator;
import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

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
        
        // TODO when createSpec is working:
//        TestApplication app = (TestApplication) mgmt.getEntityManager().createEntity( createSpec(template, platform) );
//        mgmt.getEntityManager().manage(app);
        
        // workaround until above is reacy
        TestApplication app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class)
            .configure(TestEntity.CONF_NAME, template.getName())
            .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", template.getType(), "desc", template.getDescription()))
            , mgmt);
        for (ResolvableLink<PlatformComponentTemplate> t: template.getPlatformComponentTemplates().links()) {
            app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, t.getName())
                .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", t.resolve().getType(), "desc", t.resolve().getDescription()))
                );
        }

        return new TestAppAssembly(app);
    }

    @Override
    public EntitySpec<?> createSpec(AssemblyTemplate template, CampPlatform platform, BrooklynClassLoadingContext loader) {
        EntitySpec<TestApplication> app = EntitySpec.create(TestApplication.class)
            .configure(TestEntity.CONF_NAME, template.getName())
            .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", template.getType(), "desc", template.getDescription()));
        
        for (ResolvableLink<PlatformComponentTemplate> t: template.getPlatformComponentTemplates().links()) {
            // TODO use EntitySpec.child(...)
//            app.child(EntitySpec.create(TestEntity.class)
//                .configure(TestEntity.CONF_NAME, t.getName())
//                .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", t.resolve().getType(), "desc", t.resolve().getDescription()))
//                );
        }
        
        return app;
    }

}
