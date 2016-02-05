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
package org.apache.brooklyn.rest.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.core.catalog.internal.CatalogItemBuilder;
import org.apache.brooklyn.core.catalog.internal.CatalogTemplateItemDto;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.objs.proxy.EntityProxy;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class BrooklynRestResourceUtilsTest {

    private LocalManagementContext managementContext;
    private BrooklynRestResourceUtils util;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
        util = new BrooklynRestResourceUtils(managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }

    @Test
    public void testCreateAppFromImplClass() {
        ApplicationSpec spec = ApplicationSpec.builder()
                .name("myname")
                .type(SampleNoOpApplication.class.getName())
                .locations(ImmutableSet.of("localhost"))
                .build();
        Application app = util.create(spec);
        
        assertEquals(ImmutableList.copyOf(managementContext.getApplications()), ImmutableList.of(app));
        assertEquals(app.getDisplayName(), "myname");
        assertTrue(app instanceof EntityProxy);
        assertTrue(app instanceof MyInterface);
        assertFalse(app instanceof SampleNoOpApplication);
    }

    @Test
    public void testCreateAppFromCatalogByType() {
        createAppFromCatalog(SampleNoOpApplication.class.getName());
    }

    @Test
    public void testCreateAppFromCatalogByName() {
        createAppFromCatalog("app.noop");
    }

    @Test
    public void testCreateAppFromCatalogById() {
        createAppFromCatalog("app.noop:0.0.1");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCreateAppFromCatalogByTypeMultipleItems() {
        CatalogTemplateItemDto item = CatalogItemBuilder.newTemplate("app.noop", "0.0.2-SNAPSHOT")
                .javaType(SampleNoOpApplication.class.getName())
                .build();
        managementContext.getCatalog().addItem(item);
        createAppFromCatalog(SampleNoOpApplication.class.getName());
    }

    @SuppressWarnings("deprecation")
    private void createAppFromCatalog(String type) {
        CatalogTemplateItemDto item = CatalogItemBuilder.newTemplate("app.noop", "0.0.1")
            .javaType(SampleNoOpApplication.class.getName())
            .build();
        managementContext.getCatalog().addItem(item);
        
        ApplicationSpec spec = ApplicationSpec.builder()
                .name("myname")
                .type(type)
                .locations(ImmutableSet.of("localhost"))
                .build();
        Application app = util.create(spec);

        assertEquals(app.getCatalogItemId(), "app.noop:0.0.1");
    }

    @Test
    public void testEntityAppFromCatalogByType() {
        createEntityFromCatalog(BasicEntity.class.getName());
    }

    @Test
    public void testEntityAppFromCatalogByName() {
        createEntityFromCatalog("app.basic");
    }

    @Test
    public void testEntityAppFromCatalogById() {
        createEntityFromCatalog("app.basic:0.0.1");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testEntityAppFromCatalogByTypeMultipleItems() {
        CatalogTemplateItemDto item = CatalogItemBuilder.newTemplate("app.basic", "0.0.2-SNAPSHOT")
                .javaType(SampleNoOpApplication.class.getName())
                .build();
        managementContext.getCatalog().addItem(item);
        createEntityFromCatalog(BasicEntity.class.getName());
    }

    @SuppressWarnings("deprecation")
    private void createEntityFromCatalog(String type) {
        String symbolicName = "app.basic";
        String version = "0.0.1";
        CatalogTemplateItemDto item = CatalogItemBuilder.newTemplate(symbolicName, version)
            .javaType(BasicEntity.class.getName())
            .build();
        managementContext.getCatalog().addItem(item);

        ApplicationSpec spec = ApplicationSpec.builder()
                .name("myname")
                .entities(ImmutableSet.of(new EntitySpec(type)))
                .locations(ImmutableSet.of("localhost"))
                .build();
        Application app = util.create(spec);

        Entity entity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(entity.getCatalogItemId(), CatalogUtils.getVersionedId(symbolicName, version));
    }

    @Test
    public void testNestedApplications() {
        // hierarchy is: app -> subapp -> subentity (where subentity has a policy)
        
        Application app = managementContext.getEntityManager().createEntity(org.apache.brooklyn.api.entity.EntitySpec.create(TestApplication.class)
                .displayName("app")
                .child(org.apache.brooklyn.api.entity.EntitySpec.create(TestApplication.class)
                        .displayName("subapp")
                        .child(org.apache.brooklyn.api.entity.EntitySpec.create(TestEntity.class)
                                .displayName("subentity")
                                .policy(org.apache.brooklyn.api.policy.PolicySpec.create(MyPolicy.class)
                                        .displayName("mypolicy")))));

        Application subapp = (Application) Iterables.getOnlyElement(app.getChildren());
        TestEntity subentity = (TestEntity) Iterables.getOnlyElement(subapp.getChildren());
        
        Entity subappRetrieved = util.getEntity(app.getId(), subapp.getId());
        assertEquals(subappRetrieved.getDisplayName(), "subapp");
        
        Entity subentityRetrieved = util.getEntity(app.getId(), subentity.getId());
        assertEquals(subentityRetrieved.getDisplayName(), "subentity");
        
        Policy subappPolicy = util.getPolicy(app.getId(), subentity.getId(), "mypolicy");
        assertEquals(subappPolicy.getDisplayName(), "mypolicy");
    }

    public interface MyInterface {
    }

    @Catalog(name="Sample No-Op Application",
            description="Application which does nothing, included only as part of the test cases.",
            iconUrl="")
    public static class SampleNoOpApplication extends AbstractApplication implements MyInterface {
    }
    
    public static class MyPolicy extends AbstractPolicy {
        public MyPolicy() {
        }
        public MyPolicy(Map<String, ?> flags) {
            super(flags);
        }
    }
}
