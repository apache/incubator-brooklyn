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
package io.brooklyn.camp.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collection;

import org.junit.Assert;
import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.management.osgi.OsgiStandaloneTest;

import com.google.common.collect.Iterables;


public class CatalogYamlTest extends AbstractYamlTest {
    private static final String SIMPLE_ENTITY_TYPE = "brooklyn.osgi.tests.SimpleEntity";

    @Test
    public void testAddCatalogItem() throws Exception {
        String registeredTypeName = "my.catalog.app.id.load";
        addCatalogOSGiEntity(registeredTypeName);
        
        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(registeredTypeName, TEST_VERSION);
        assertEquals(item.getRegisteredTypeName(), registeredTypeName);
    }

    @Test
    public void testAddCatalogItemWithoutVersionFail() throws Exception {
        try {
            addCatalogItem(
                "brooklyn.catalog:",
                "  name: My Catalog App",
                "services:",
                "- type: " + SIMPLE_ENTITY_TYPE);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "'version' attribute missing in 'brooklyn.catalog' section.");
        }
    }

    @Test
    public void testLaunchApplicationReferencingCatalog() throws Exception {
        String registeredTypeName = "my.catalog.app.id.launch";
        registerAndLaunchAndAssertSimpleEntity(registeredTypeName, SIMPLE_ENTITY_TYPE);
    }

    @Test
    public void testLaunchApplicationReferencingUnversionedCatalogFail() throws Exception {
        String registeredTypeName = "my.catalog.app.id.fail";
        addCatalogOSGiEntity(registeredTypeName, SIMPLE_ENTITY_TYPE);
        try {
            String yaml = "name: simple-app-yaml\n" +
                          "location: localhost\n" +
                          "services: \n" +
                          "  - serviceType: " + registeredTypeName;
            try {
                createAndStartApplication(yaml);
            } catch (UnsupportedOperationException e) {
                assertTrue(e.getMessage().endsWith("cannot be matched"));
            }
        } finally {
            deleteCatalogEntity(registeredTypeName);
        }
    }

    @Test
    public void testLaunchApplicationWithCatalogReferencingOtherCatalog() throws Exception {
        String referencedRegisteredTypeName = "my.catalog.app.id.referenced";
        String referrerRegisteredTypeName = "my.catalog.app.id.referring";
        addCatalogOSGiEntity(referencedRegisteredTypeName, SIMPLE_ENTITY_TYPE);
        addCatalogOSGiEntity(referrerRegisteredTypeName, ver(referencedRegisteredTypeName));

        String yaml = "name: simple-app-yaml\n" +
                      "location: localhost\n" +
                      "services: \n" +
                      "  - serviceType: " + ver(referrerRegisteredTypeName);
        Entity app = createAndStartApplication(yaml);
        
        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);
    }

    @Test
    public void testLaunchApplicationChildWithCatalogReferencingOtherCatalog() throws Exception {
        String referencedRegisteredTypeName = "my.catalog.app.id.child.referenced";
        String referrerRegisteredTypeName = "my.catalog.app.id.child.referring";
        addCatalogOSGiEntity(referencedRegisteredTypeName, SIMPLE_ENTITY_TYPE);
        addCatalogChildOSGiEntity(referrerRegisteredTypeName, ver(referencedRegisteredTypeName));

        Entity app = createAndStartApplication(
            "name: simple-app-yaml",
            "location: localhost",
            "services:",
            "- serviceType: "+BasicEntity.class.getName(),
            "  brooklyn.children:",
            "  - type: " + ver(referrerRegisteredTypeName));
        
        Collection<Entity> children = app.getChildren();
        assertEquals(children.size(), 1);
        Entity child = Iterables.getOnlyElement(children);
        assertEquals(child.getEntityType().getName(), BasicEntity.class.getName());
        Collection<Entity> grandChildren = child.getChildren();
        assertEquals(grandChildren.size(), 1);
        Entity grandChild = Iterables.getOnlyElement(grandChildren);
        assertEquals(grandChild.getEntityType().getName(), BasicEntity.class.getName());
        Collection<Entity> grandGrandChildren = grandChild.getChildren();
        assertEquals(grandGrandChildren.size(), 1);
        Entity grandGrandChild = Iterables.getOnlyElement(grandGrandChildren);
        assertEquals(grandGrandChild.getEntityType().getName(), SIMPLE_ENTITY_TYPE);
    }

    @Test
    public void testLaunchApplicationWithTypeUsingJavaColonPrefix() throws Exception {
        String registeredTypeName = SIMPLE_ENTITY_TYPE;
        String serviceName = "java:"+SIMPLE_ENTITY_TYPE;
        registerAndLaunchAndAssertSimpleEntity(registeredTypeName, serviceName);
    }

    @Test
    public void testLaunchApplicationLoopWithJavaTypeName() throws Exception {
        String registeredTypeName = SIMPLE_ENTITY_TYPE;
        String serviceName = SIMPLE_ENTITY_TYPE;
        registerAndLaunchAndAssertSimpleEntity(registeredTypeName, serviceName);
    }

    @Test
    public void testLaunchApplicationLoopCatalogIdFails() throws Exception {
        String registeredTypeName = "self.referencing.type";
        registerAndLaunchFailsWithRecursionError(registeredTypeName);
    }

    @Test
    public void testLaunchApplicationChildLoopCatalogIdFails() throws Exception {
        String referrerRegisteredTypeName = "my.catalog.app.id.child.referring";
        addCatalogChildOSGiEntity(referrerRegisteredTypeName, ver(referrerRegisteredTypeName));

        try {
            createAndStartApplication(
                "name: simple-app-yaml",
                "location: localhost",
                "services:",
                "- serviceType: "+BasicEntity.class.getName(),
                "  brooklyn.children:",
                "  - type: " + ver(referrerRegisteredTypeName));

                fail("Expected to throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Recursive reference to "+ver(referrerRegisteredTypeName)));
        } finally {
            deleteCatalogEntity(referrerRegisteredTypeName);
        }
    }

    /**
     * Tests that a catalog item referenced by another
     * catalog item won't have access to the parent's bundles.
     */
    @Test
    public void testParentCatalogDoesNotLeakBundlesToChildCatalogItems() throws Exception {
        String childCatalogId = "my.catalog.app.id.no_bundles";
        String parentCatalogId = "my.catalog.app.id.parent";
        addCatalogItem(
                "brooklyn.catalog:",
                "  id: " + childCatalogId,
                "  version: " + TEST_VERSION,
                "",
                "services:",
                "- type: " + SIMPLE_ENTITY_TYPE);
        
        addCatalogItem(
                "brooklyn.catalog:",
                "  id: " + parentCatalogId,
                "  version: " + TEST_VERSION,
                "  libraries:",
                "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
                "",
                "services:",
                "- type: " + ver(childCatalogId));
        
        try {
            createAndStartApplication(
                    "services:",
                    "- type: " + ver(parentCatalogId));
            fail();
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().endsWith("cannot be matched"));
            assertTrue(e.getMessage().contains(SIMPLE_ENTITY_TYPE));
        }

        deleteCatalogEntity(parentCatalogId);
        deleteCatalogEntity(childCatalogId);
    }

    @Test
    public void testReferenceInstalledBundleByName() {
        String firstItemId = "my.catalog.app.id.register_bundle";
        String secondItemId = "my.catalog.app.id.reference_bundle";
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + firstItemId,
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: " + SIMPLE_ENTITY_TYPE);
        deleteCatalogEntity(firstItemId);

        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + secondItemId,
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - name: io.brooklyn.brooklyn-test-osgi-entities",
            "    version: 0.1.0",
            "",
            "services:",
            "- type: " + SIMPLE_ENTITY_TYPE);

        deleteCatalogEntity(secondItemId);
    }

    @Test
    public void testReferenceNonInstalledBundledByNameFails() {
        String nonExistentId = "none-existent-id";
        String nonExistentVersion = "9.9.9";
        try {
            addCatalogItem(
                "brooklyn.catalog:",
                "  id: my.catalog.app.id.non_existing.ref",
                "  version: " + TEST_VERSION,
                "  libraries:",
                "  - name: " + nonExistentId,
                "    version: " + nonExistentVersion,
                "",
                "services:",
                "- type: " + SIMPLE_ENTITY_TYPE);
            fail();
        } catch (IllegalStateException e) {
            Assert.assertEquals(e.getMessage(), "Bundle CatalogBundleDto{name=" + nonExistentId + ", version=" + nonExistentVersion + ", url=null} not already registered by name:version, but URL is empty.");
        }
    }

    @Test
    public void testPartialBundleReferenceFails() {
        try {
            addCatalogItem(
                "brooklyn.catalog:",
                "  id: my.catalog.app.id.non_existing.ref",
                "  version: " + TEST_VERSION,
                "  libraries:",
                "  - name: io.brooklyn.brooklyn-test-osgi-entities",
                "",
                "services:",
                "- type: " + SIMPLE_ENTITY_TYPE);
            fail();
        } catch (NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "version");
        }
        try {
            addCatalogItem(
                "brooklyn.catalog:",
                "  id: my.catalog.app.id.non_existing.ref",
                "  version: " + TEST_VERSION,
                "  libraries:",
                "  - version: 0.1.0",
                "",
                "services:",
                "- type: " + SIMPLE_ENTITY_TYPE);
            fail();
        } catch (NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "name");
        }
    }

    @Test
    public void testFullBundleReference() {
        String itemId = "my.catalog.app.id.full_ref";
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + itemId,
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - name: io.brooklyn.brooklyn-test-osgi-entities",
            "    version: 0.1.0",
            "    url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: " + SIMPLE_ENTITY_TYPE);
        deleteCatalogEntity(itemId);
    }

    /**
     * Test that the name:version contained in the OSGi bundle will
     * override the values supplied in the YAML.
     */
    @Test
    public void testFullBundleReferenceUrlMetaOverridesLocalNameVersion() {
        String firstItemId = "my.catalog.app.id.register_bundle";
        String secondItemId = "my.catalog.app.id.reference_bundle";
        String nonExistentId = "non_existent_id";
        String nonExistentVersion = "9.9.9";
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + firstItemId,
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - name: " + nonExistentId,
            "    version: " + nonExistentVersion,
            "    url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: " + SIMPLE_ENTITY_TYPE);
        deleteCatalogEntity(firstItemId);

        try {
            addCatalogItem(
                "brooklyn.catalog:",
                "  id: " + secondItemId,
                "  version: " + TEST_VERSION,
                "  libraries:",
                "  - name: " + nonExistentId,
                "    version: " + nonExistentVersion,
                "",
                "services:",
                "- type: " + SIMPLE_ENTITY_TYPE);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Bundle CatalogBundleDto{name=" + nonExistentId + ", version=" + nonExistentVersion + ", url=null} " +
                    "not already registered by name:version, but URL is empty.");
        }
    }

    private void registerAndLaunchAndAssertSimpleEntity(String registeredTypeName, String serviceType) throws Exception {
        addCatalogOSGiEntity(registeredTypeName, serviceType);
        try {
            String yaml = "name: simple-app-yaml\n" +
                          "location: localhost\n" +
                          "services: \n" +
                          "  - serviceType: " + ver(registeredTypeName);
            Entity app = createAndStartApplication(yaml);
    
            Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
            assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);
        } finally {
            deleteCatalogEntity(registeredTypeName);
        }
    }

    private void registerAndLaunchFailsWithRecursionError(String registeredTypeName) throws Exception {
        addCatalogOSGiEntity(registeredTypeName, ver(registeredTypeName));
        try {
            String yaml = "name: simple-app-yaml\n" +
                          "location: localhost\n" +
                          "services: \n" +
                          "  - serviceType: " + ver(registeredTypeName);
            try {
                createAndStartApplication(yaml);
                fail("Expected to throw IllegalStateException");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Recursive reference to "+registeredTypeName));
            }
        } finally {
            deleteCatalogEntity(registeredTypeName);
        }
    }

    private void addCatalogOSGiEntity(String registeredTypeName) {
        addCatalogOSGiEntity(registeredTypeName, SIMPLE_ENTITY_TYPE);
    }
    
    private void addCatalogOSGiEntity(String registeredTypeName, String serviceType) {
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + registeredTypeName,
            "  name: My Catalog App",
            "  description: My description",
            "  icon_url: classpath://path/to/myicon.jpg",
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: " + serviceType);
    }

    private void addCatalogChildOSGiEntity(String registeredTypeName, String serviceType) {
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + registeredTypeName,
            "  name: My Catalog App",
            "  description: My description",
            "  icon_url: classpath://path/to/myicon.jpg",
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: " + BasicEntity.class.getName(),
            "  brooklyn.children:",
            "  - type: " + serviceType);
    }
}
