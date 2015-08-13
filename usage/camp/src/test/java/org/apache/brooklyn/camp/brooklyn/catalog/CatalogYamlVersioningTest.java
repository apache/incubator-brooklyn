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
package org.apache.brooklyn.camp.brooklyn.catalog;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.catalog.BrooklynCatalog;
import org.apache.brooklyn.catalog.CatalogItem;

import brooklyn.catalog.CatalogPredicates;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.ConfigKeys;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class CatalogYamlVersioningTest extends AbstractYamlTest {
    
    private BrooklynCatalog catalog;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        super.setUp();
        catalog = mgmt().getCatalog();
    }

    @Test
    public void testAddItem() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        addCatalogEntity(symbolicName, version);
        assertSingleCatalogItem(symbolicName, version);
    }

    @Test
    public void testAddUnversionedItem() {
        String symbolicName = "sampleId";
        addCatalogEntity(symbolicName, null);
        assertSingleCatalogItem(symbolicName, BasicBrooklynCatalog.NO_VERSION);
    }

    @Test
    public void testAddSameVersionFailsWhenIconIsDifferent() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        addCatalogEntity(symbolicName, version);
        addCatalogEntity(symbolicName, version);
        try {
            addCatalogEntity(symbolicName, version, BasicEntity.class.getName(), "classpath:/another/icon.png");
            fail("Expected to fail");
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Updating existing catalog entries is forbidden: " + symbolicName + ":" + version + ". Use forceUpdate argument to override.");
        }
    }
    
    @Test
    public void testAddSameVersionForce() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        addCatalogEntity(symbolicName, version);
        forceCatalogUpdate();
        String expectedType = "brooklyn.entity.basic.BasicApplication";
        addCatalogEntity(symbolicName, version, expectedType);
        CatalogItem<?, ?> item = catalog.getCatalogItem(symbolicName, version);
        assertTrue(item.getPlanYaml().contains(expectedType), "Version not updated");
    }
    
    @Test
    public void testGetLatest() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0";
        addCatalogEntity(symbolicName, v1);
        addCatalogEntity(symbolicName, v2);
        CatalogItem<?, ?> item = catalog.getCatalogItem(symbolicName, BasicBrooklynCatalog.DEFAULT_VERSION);
        assertEquals(item.getVersion(), v2);
    }
    
    @Test
    public void testGetLatestStable() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0-SNAPSHOT";
        addCatalogEntity(symbolicName, v1);
        addCatalogEntity(symbolicName, v2);
        CatalogItem<?, ?> item = catalog.getCatalogItem(symbolicName, BasicBrooklynCatalog.DEFAULT_VERSION);
        assertEquals(item.getVersion(), v1);
    }

    @Test
    public void testDelete() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        addCatalogEntity(symbolicName, version);
        assertTrue(catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName))).iterator().hasNext());
        catalog.deleteCatalogItem(symbolicName, version);
        assertFalse(catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName))).iterator().hasNext());
    }
    
    @Test
    public void testDeleteDefault() {
        String symbolicName = "sampleId";
        addCatalogEntity(symbolicName, null);
        assertTrue(catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName))).iterator().hasNext());
        catalog.deleteCatalogItem(symbolicName, BasicBrooklynCatalog.NO_VERSION);
        assertFalse(catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName))).iterator().hasNext());
    }
    
    @Test
    public void testList() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0-SNAPSHOT";
        addCatalogEntity(symbolicName, v1);
        addCatalogEntity(symbolicName, v2);
        Iterable<CatalogItem<Object, Object>> items = catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName)));
        assertEquals(Iterables.size(items), 2);
    }
    
    @Test
    public void testVersionedReference() throws Exception {
        String symbolicName = "sampleId";
        String parentName = "parentId";
        String v1 = "0.1.0";
        String v2 = "0.2.0";
        String expectedType = BasicApplication.class.getName();

        addCatalogEntity(symbolicName, v1, expectedType);
        addCatalogEntity(symbolicName, v2);
        addCatalogEntity(parentName, v1, symbolicName + ":" + v1);

        Entity app = createAndStartApplication(
                "services:",
                "- type: " + parentName + ":" + v1);

        assertEquals(app.getEntityType().getName(), expectedType);
    }

    @Test
    public void testUnversionedReference() throws Exception {
        String symbolicName = "sampleId";
        String parentName = "parentId";
        String v1 = "0.1.0";
        String v2 = "0.2.0";
        String expectedType = BasicApplication.class.getName();

        addCatalogEntity(symbolicName, v1);
        addCatalogEntity(symbolicName, v2, expectedType);
        addCatalogEntity(parentName, v1, symbolicName);

        Entity app = createAndStartApplication(
                "services:",
                "- type: " + parentName + ":" + v1);

        assertEquals(app.getEntityType().getName(), expectedType);
    }

    private void doTestVersionedReferenceJustAdded(boolean isVersionImplicitSyntax) throws Exception {
        addCatalogItems(            "brooklyn.catalog:",
            "  version: 0.9",
            "  items:",
            "  - id: referrent",
            "    item:",
            "      type: "+BasicEntity.class.getName(),
            "  - id: referrent",
            "    version: 1.1",
            "    item:",
            "      type: "+BasicEntity.class.getName(),
            "      brooklyn.config: { foo: bar }",
            "  - id: referrer",
            "    version: 1.0",
            "    item:",
            (isVersionImplicitSyntax ? 
                "      type: referrent:1.1" :
                "      type: referrent\n" +
                "      version: 1.1"));
        
        Iterable<CatalogItem<Object, Object>> items = catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo("referrer")));
        Assert.assertEquals(Iterables.size(items), 1, "Wrong number of: "+items);
        CatalogItem<Object, Object> item = Iterables.getOnlyElement(items);
        Assert.assertEquals(item.getVersion(), "1.0");
        
        Entity app = createAndStartApplication(
            "services:",
            (isVersionImplicitSyntax ? 
                "- type: referrer:1.0" :
                "- type: referrer\n" +
                "  version: 1.0") );
        Entity child = Iterables.getOnlyElement(app.getChildren());
        Assert.assertTrue(child instanceof BasicEntity, "Wrong child: "+child);
        Assert.assertEquals(child.getConfig(ConfigKeys.newStringConfigKey("foo")), "bar");
    }

    @Test
    public void testVersionedReferenceJustAddedExplicitVersion() throws Exception {
        doTestVersionedReferenceJustAdded(false);
    }
    
    @Test
    public void testVersionedReferenceJustAddedImplicitVersionSyntax() throws Exception {
        doTestVersionedReferenceJustAdded(true);
    }
    
    private void assertSingleCatalogItem(String symbolicName, String version) {
        Iterable<CatalogItem<Object, Object>> items = catalog.getCatalogItems(CatalogPredicates.symbolicName(Predicates.equalTo(symbolicName)));
        CatalogItem<Object, Object> item = Iterables.getOnlyElement(items);
        assertEquals(item.getSymbolicName(), symbolicName);
        assertEquals(item.getVersion(), version);
    }
    
    private void addCatalogEntity(String symbolicName, String version) {
        addCatalogEntity(symbolicName, version, BasicEntity.class.getName());
    }

    private void addCatalogEntity(String symbolicName, String version, String type) {
        addCatalogEntity(symbolicName, version, type, "classpath://path/to/myicon.jpg");
    }
    
    private void addCatalogEntity(String symbolicName, String version, String type, String iconUrl) {
        addCatalogItems(
            "brooklyn.catalog:",
            "  id: " + symbolicName,
            "  name: My Catalog App",
            "  description: My description",
            "  icon_url: "+iconUrl,
            (version != null ? "  version: " + version : ""),
            "",
            "services:",
            "- type: " + type);
    }

}
