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
package org.apache.brooklyn.core.catalog.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.core.catalog.CatalogPredicates;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.catalog.internal.CatalogItemBuilder;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class CatalogVersioningTest {
    private LocalManagementContext managementContext;
    private BrooklynCatalog catalog;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
        catalog = managementContext.getCatalog();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testParsingVersion() {
        assertVersionParsesAs("foo:1", "foo", "1");
        assertVersionParsesAs("foo", null, null);
        assertVersionParsesAs("foo:1.1", "foo", "1.1");
        assertVersionParsesAs("foo:1_SNAPSHOT", "foo", "1_SNAPSHOT");
        assertVersionParsesAs("foo:10.9.8_SNAPSHOT", "foo", "10.9.8_SNAPSHOT");
        assertVersionParsesAs("foo:bar", null, null);
        assertVersionParsesAs("chef:cookbook", null, null);
        assertVersionParsesAs("http://foo:8080", null, null);
    }

    private static void assertVersionParsesAs(String versionedId, String id, String version) {
        if (version==null) {
            Assert.assertFalse(CatalogUtils.looksLikeVersionedId(versionedId));
        } else {
            Assert.assertTrue(CatalogUtils.looksLikeVersionedId(versionedId));
            Assert.assertEquals(CatalogUtils.getSymbolicNameFromVersionedId(versionedId), id);
            Assert.assertEquals(CatalogUtils.getVersionFromVersionedId(versionedId), version);
        }
    }

    @Test
    public void testAddVersioned() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        createCatalogItem(symbolicName, version);
        assertSingleCatalogItem(symbolicName, version);
    }

    @Test
    public void testAddSameVersionFails() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        createCatalogItem(symbolicName, version);
        createCatalogItem(symbolicName, version);
        //forced update assumed in the above call
        assertSingleCatalogItem(symbolicName, version);
    }
    
    @Test
    public void testGetLatest() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0";
        createCatalogItem(symbolicName, v1);
        createCatalogItem(symbolicName, v2);
        CatalogItem<?, ?> item = catalog.getCatalogItem(symbolicName, BasicBrooklynCatalog.DEFAULT_VERSION);
        assertEquals(item.getSymbolicName(), symbolicName);
        assertEquals(item.getVersion(), v2);
    }
    
    @Test
    public void testGetLatestStable() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0-SNAPSHOT";
        createCatalogItem(symbolicName, v1);
        createCatalogItem(symbolicName, v2);
        CatalogItem<?, ?> item = catalog.getCatalogItem(symbolicName, BasicBrooklynCatalog.DEFAULT_VERSION);
        assertEquals(item.getSymbolicName(), symbolicName);
        assertEquals(item.getVersion(), v1);
    }
    
    @Test
    public void testGetLatestSkipsDisabled() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0";
        createCatalogItem(symbolicName, v1);
        createCatalogItem(symbolicName, v2);
        disableCatalogItem(symbolicName, v2);
        
        CatalogItem<?, ?> item = catalog.getCatalogItem(symbolicName, BasicBrooklynCatalog.DEFAULT_VERSION);
        assertEquals(item.getSymbolicName(), symbolicName);
        assertEquals(item.getVersion(), v1);
    }
    
    @Test
    public void testDelete() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        createCatalogItem(symbolicName, version);
        assertSingleCatalogItem(symbolicName, version);
        assertTrue(catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName))).iterator().hasNext());
        catalog.deleteCatalogItem(symbolicName, version);
        assertFalse(catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName))).iterator().hasNext());
    }

    @Test
    public void testList() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0-SNAPSHOT";
        createCatalogItem(symbolicName, v1);
        createCatalogItem(symbolicName, v2);
        Iterable<CatalogItem<Object, Object>> items = catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName)));
        assertEquals(Iterables.size(items), 2);
    }
    
    @SuppressWarnings("deprecation")
    private void createCatalogItem(String symbolicName, String version) {
        catalog.addItem(CatalogItemBuilder.newEntity(symbolicName, version).
                plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());
    }

    private void disableCatalogItem(String symbolicName, String version) {
        CatalogItem<?, ?> item = catalog.getCatalogItem(symbolicName, version);
        item.setDisabled(true);
        catalog.persist(item);
    }

    private void assertSingleCatalogItem(String symbolicName, String version) {
        Iterable<CatalogItem<Object, Object>> items = catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName)));
        CatalogItem<Object, Object> item = Iterables.getOnlyElement(items);
        assertEquals(item.getSymbolicName(), symbolicName);
        assertEquals(item.getVersion(), version);
    }


}
