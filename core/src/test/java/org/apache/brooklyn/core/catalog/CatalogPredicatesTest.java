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
package org.apache.brooklyn.core.catalog;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.core.catalog.internal.CatalogItemBuilder;
import org.apache.brooklyn.core.management.internal.LocalManagementContext;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;

import brooklyn.entity.basic.Entities;

public class CatalogPredicatesTest {
    private LocalManagementContext mgmt;
    private BrooklynCatalog catalog;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        mgmt = LocalManagementContextForTests.newInstance();
        catalog = mgmt.getCatalog();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    @Test
    public void testDisplayName() {
        CatalogItem<Entity, EntitySpec<?>> item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .displayName("myname")
                .build());

        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>displayName(Predicates.equalTo("myname")).apply(item));
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>displayName(Predicates.equalTo("wrongname")).apply(item));
    }
    
    @Test
    public void testDeprecated() {
        CatalogItem<Entity, EntitySpec<?>> item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .build());

        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>deprecated(false).apply(item));
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>deprecated(true).apply(item));
        
        item = deprecateItem(item);
        
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>deprecated(false).apply(item));
        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>deprecated(true).apply(item));
    }
    
    @Test
    public void testIsCatalogItemType() {
        CatalogItem<Entity, EntitySpec<?>> item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .build());

        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>isCatalogItemType(CatalogItemType.ENTITY).apply(item));
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>isCatalogItemType(CatalogItemType.LOCATION).apply(item));
    }
    
    @Test
    public void testSymbolicName() {
        CatalogItem<Entity, EntitySpec<?>> item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .build());

        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>symbolicName(Predicates.equalTo("foo")).apply(item));
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>symbolicName(Predicates.equalTo("wrongname")).apply(item));
    }

    @Test
    public void testIsBestVersion() {
        CatalogItem<Entity, EntitySpec<?>> itemV1 = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .build());
        CatalogItem<Entity, EntitySpec<?>> itemV2 = createItem(CatalogItemBuilder.newEntity("foo", "2.0")
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .build());
        CatalogItem<Entity, EntitySpec<?>> itemV3Disabled = createItem(CatalogItemBuilder.newEntity("foo", "3.0")
                .disabled(true)
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .build());

        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>isBestVersion(mgmt).apply(itemV2));
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>isBestVersion(mgmt).apply(itemV1));
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>isBestVersion(mgmt).apply(itemV3Disabled));
    }

    @Test
    public void testEntitledToSee() {
        // TODO No entitlements configured, so everything allowed - therefore test not thorough enough!
        CatalogItem<Entity, EntitySpec<?>> item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: brooklyn.entity.basic.BasicEntity")
                .build());

        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>entitledToSee(mgmt).apply(item));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testJavaType() {
        CatalogItem<Entity, EntitySpec<?>> item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .javaType("brooklyn.entity.basic.BasicEntity")
                .build());

        assertTrue(CatalogPredicates.<Entity,EntitySpec<?>>javaType(Predicates.equalTo("brooklyn.entity.basic.BasicEntity")).apply(item));
        assertFalse(CatalogPredicates.<Entity,EntitySpec<?>>javaType(Predicates.equalTo("wrongtype")).apply(item));
    }

    @SuppressWarnings("deprecation")
    protected <T, SpecT> CatalogItem<T, SpecT> createItem(CatalogItem<T, SpecT> item) {
        catalog.addItem(item);
        return item;
    }
    
    @SuppressWarnings("unchecked")
    protected <T, SpecT> CatalogItem<T, SpecT> deprecateItem(CatalogItem<T, SpecT> orig) {
        CatalogItem<T, SpecT> item = (CatalogItem<T, SpecT>) catalog.getCatalogItem(orig.getSymbolicName(), orig.getVersion());
        item.setDeprecated(true);
        catalog.persist(item);
        return item;
    }
}
