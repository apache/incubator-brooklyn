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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.typereg.RegisteredTypePredicates;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class CatalogYamlVersioningTest extends AbstractYamlTest {
    
    private BrooklynTypeRegistry types;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        super.setUp();
        types = mgmt().getTypeRegistry();
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
        String expectedType = "org.apache.brooklyn.entity.stock.BasicApplication";
        addCatalogEntity(symbolicName, version, expectedType);
        RegisteredType item = types.get(symbolicName, version);
        String yaml = RegisteredTypes.getImplementationDataStringForSpec(item);
        assertTrue(yaml.contains(expectedType), "Version not updated:\n"+yaml);
    }
    
    @Test
    public void testGetLatest() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0";
        addCatalogEntity(symbolicName, v1);
        addCatalogEntity(symbolicName, v2);
        RegisteredType item = types.get(symbolicName, BasicBrooklynCatalog.DEFAULT_VERSION);
        assertEquals(item.getVersion(), v2);
    }
    
    @Test
    public void testGetLatestStable() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0-SNAPSHOT";
        addCatalogEntity(symbolicName, v1);
        addCatalogEntity(symbolicName, v2);
        RegisteredType item = types.get(symbolicName, BasicBrooklynCatalog.DEFAULT_VERSION);
        assertEquals(item.getVersion(), v1);
    }

    @Test
    public void testDelete() {
        String symbolicName = "sampleId";
        String version = "0.1.0";
        addCatalogEntity(symbolicName, version);
        
        Iterable<RegisteredType> matches;
        matches = types.getAll(RegisteredTypePredicates.symbolicName(Predicates.equalTo(symbolicName)));
        assertTrue(matches.iterator().hasNext());
        
        mgmt().getCatalog().deleteCatalogItem(symbolicName, version);
        matches = types.getAll(RegisteredTypePredicates.symbolicName(Predicates.equalTo(symbolicName)));
        assertFalse(matches.iterator().hasNext());
    }
    
    @Test
    public void testDeleteDefault() {
        String symbolicName = "sampleId";
        addCatalogEntity(symbolicName, null);

        Iterable<RegisteredType> matches;
        matches = types.getAll(RegisteredTypePredicates.symbolicName(Predicates.equalTo(symbolicName)));
        assertTrue(matches.iterator().hasNext());
        
        mgmt().getCatalog().deleteCatalogItem(symbolicName, BasicBrooklynCatalog.NO_VERSION);
        matches = types.getAll(RegisteredTypePredicates.symbolicName(Predicates.equalTo(symbolicName)));
        assertFalse(matches.iterator().hasNext());
    }
    
    @Test
    public void testList() {
        String symbolicName = "sampleId";
        String v1 = "0.1.0";
        String v2 = "0.2.0-SNAPSHOT";
        addCatalogEntity(symbolicName, v1);
        addCatalogEntity(symbolicName, v2);
        Iterable<RegisteredType> items = types.getAll(RegisteredTypePredicates.symbolicName(Predicates.equalTo(symbolicName)));
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
        
        Iterable<RegisteredType> items = types.getAll(RegisteredTypePredicates.symbolicName(Predicates.equalTo("referrer")));
        Assert.assertEquals(Iterables.size(items), 1, "Wrong number of: "+items);
        RegisteredType item = Iterables.getOnlyElement(items);
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
        Iterable<RegisteredType> items = types.getAll(RegisteredTypePredicates.symbolicName(Predicates.equalTo(symbolicName)));
        RegisteredType item = Iterables.getOnlyElement(items);
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
