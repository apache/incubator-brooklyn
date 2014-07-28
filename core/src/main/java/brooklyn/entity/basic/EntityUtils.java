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
package brooklyn.entity.basic;

import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.CatalogItemDtoAbstract;
import brooklyn.entity.Application;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.internal.SpecCreator;
import brooklyn.internal.SpecCreatorFactory;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;

public class EntityUtils {
    private static final Logger log = LoggerFactory.getLogger(EntityUtils.class);

    public static Task<Void> launch(ManagementContext mgmt, Application app) {
        return Entities.invokeEffector((EntityLocal)app, app, Startable.START,
                // locations already set in the entities themselves;
                // TODO make it so that this arg does not have to be supplied to START !
                MutableMap.of("locations", MutableList.of()));
    }

    public static Application createApp(ManagementContext mgmt, EntitySpec<?> spec) {
        Application app = (Application) mgmt.getEntityManager().createEntity(spec);
        log.info("Placing '{}' under management", app);
        Entities.startManagement(app, mgmt);
        return app;
    }

    public static Application createApp(ManagementContext mgmt, String yaml) {
        EntitySpec<?> spec = createSpec(mgmt, yaml);
        return createApp(mgmt, spec);
    }

    public static Application createApp(ManagementContext mgmt, Reader reader) {
        EntitySpec<?> spec = createSpec(mgmt, reader);
        return createApp(mgmt, spec);
    }

    public static EntitySpec<?> createSpec(ManagementContext mgmt, String yaml) {
        return (EntitySpec<?>) getSpecCreator().createSpec(mgmt, yaml, null);
    }

    public static EntitySpec<?> createSpec(ManagementContext mgmt, String yaml, BrooklynClassLoadingContext loader) {
        return (EntitySpec<?>) getSpecCreator().createSpec(mgmt, yaml, loader);
    }

    public static EntitySpec<?> createSpec(ManagementContext mgmt, Reader reader) {
        return (EntitySpec<?>) getSpecCreator().createSpec(mgmt, reader, null);
    }

    public static CatalogItemDtoAbstract<?, ?> createCatalogItem(ManagementContext mgmt, String yaml) {
        return getSpecCreator().createCatalogItem(mgmt, yaml);
    }
    private static SpecCreator getSpecCreator() {
        SpecCreator specCreator = SpecCreatorFactory.forMime(SpecCreatorFactory.YAML_CAMP_PLAN_TYPE);
        return specCreator;
    }
}
