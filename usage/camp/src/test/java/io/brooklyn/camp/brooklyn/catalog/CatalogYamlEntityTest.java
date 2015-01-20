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
package io.brooklyn.camp.brooklyn.catalog;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import brooklyn.test.TestResourceUnavailableException;
import brooklyn.util.ResourceUtils;
import io.brooklyn.camp.brooklyn.AbstractYamlTest;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.management.osgi.OsgiTestResources;

import com.google.common.collect.Iterables;


public class CatalogYamlEntityTest extends AbstractYamlTest {
    
    private static final String SIMPLE_ENTITY_TYPE = OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_ENTITY;

    @Test
    public void testAddCatalogItem() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String symbolicName = "my.catalog.app.id.load";
        addCatalogOSGiEntity(symbolicName);
        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(symbolicName, TEST_VERSION);
        assertEquals(item.getSymbolicName(), symbolicName);

        deleteCatalogEntity(symbolicName);
    }

    @Test
    public void testAddCatalogItemWithoutVersion() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String id = "unversioned.app";
        addCatalogItem(
            "brooklyn.catalog:",
            "  name: " + id,
            "  libraries:",
            "  - " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "services:",
            "- type: " + SIMPLE_ENTITY_TYPE);
        CatalogItem<?, ?> catalogItem = mgmt().getCatalog().getCatalogItem(id, BrooklynCatalog.DEFAULT_VERSION);
        assertEquals(catalogItem.getVersion(), "0.0.0.SNAPSHOT");
        mgmt().getCatalog().deleteCatalogItem(id, "0.0.0.SNAPSHOT");
    }

    @Test
    public void testLaunchApplicationReferencingCatalog() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String symbolicName = "my.catalog.app.id.launch";
        registerAndLaunchAndAssertSimpleEntity(symbolicName, SIMPLE_ENTITY_TYPE);
    }

    @Test
    public void testLaunchApplicationUnverionedCatalogReference() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String symbolicName = "my.catalog.app.id.fail";
        addCatalogOSGiEntity(symbolicName, SIMPLE_ENTITY_TYPE);
        try {
            String yaml = "name: simple-app-yaml\n" +
                          "location: localhost\n" +
                          "services: \n" +
                          "  - serviceType: " + symbolicName;
            createAndStartApplication(yaml);
        } finally {
            deleteCatalogEntity(symbolicName);
        }
    }

    @Test
    public void testLaunchApplicationWithCatalogReferencingOtherCatalog() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String referencedSymbolicName = "my.catalog.app.id.referenced";
        String referrerSymbolicName = "my.catalog.app.id.referring";
        addCatalogOSGiEntity(referencedSymbolicName, SIMPLE_ENTITY_TYPE);
        addCatalogOSGiEntity(referrerSymbolicName, ver(referencedSymbolicName));

        String yaml = "name: simple-app-yaml\n" +
                      "location: localhost\n" +
                      "services: \n" +
                      "  - serviceType: " + ver(referrerSymbolicName);
        Entity app = createAndStartApplication(yaml);

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);

        deleteCatalogEntity(referencedSymbolicName);
        deleteCatalogEntity(referrerSymbolicName);
    }

    @Test
    public void testLaunchApplicationChildWithCatalogReferencingOtherCatalog() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String referencedSymbolicName = "my.catalog.app.id.child.referenced";
        String referrerSymbolicName = "my.catalog.app.id.child.referring";
        addCatalogOSGiEntity(referencedSymbolicName, SIMPLE_ENTITY_TYPE);
        addCatalogChildOSGiEntity(referrerSymbolicName, ver(referencedSymbolicName));

        Entity app = createAndStartApplication(
            "name: simple-app-yaml",
            "location: localhost",
            "services:",
            "- serviceType: "+BasicEntity.class.getName(),
            "  brooklyn.children:",
            "  - type: " + ver(referrerSymbolicName));

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

        deleteCatalogEntity(referencedSymbolicName);
        deleteCatalogEntity(referrerSymbolicName);
    }

    @Test
    public void testLaunchApplicationWithTypeUsingJavaColonPrefix() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String symbolicName = SIMPLE_ENTITY_TYPE;
        String serviceName = "java:"+SIMPLE_ENTITY_TYPE;
        registerAndLaunchAndAssertSimpleEntity(symbolicName, serviceName);
    }

    @Test
    public void testLaunchApplicationLoopWithJavaTypeName() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String symbolicName = SIMPLE_ENTITY_TYPE;
        String serviceName = SIMPLE_ENTITY_TYPE;
        registerAndLaunchAndAssertSimpleEntity(symbolicName, serviceName);
    }

    @Test
    public void testLaunchApplicationChildLoopCatalogIdFails() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String referrerSymbolicName = "my.catalog.app.id.child.referring";
        try {
            addCatalogChildOSGiEntity(referrerSymbolicName, ver(referrerSymbolicName));
            fail("Expected to throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Could not find "+referrerSymbolicName));
        }
    }

    @Test
    public void testReferenceInstalledBundleByName() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

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
            "  - name: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_NAME,
            "    version: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_VERSION,
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
            Assert.assertEquals(e.getMessage(), "Bundle CatalogBundleDto{symbolicName=" + nonExistentId + ", version=" + nonExistentVersion + ", url=null} not previously registered, but URL is empty.");
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
                "  - version: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_VERSION,
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
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String itemId = "my.catalog.app.id.full_ref";
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + itemId,
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - name: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_NAME,
            "    version: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_VERSION,
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
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String firstItemId = "my.catalog.app.id.register_bundle";
        String nonExistentId = "non_existent_id";
        String nonExistentVersion = "9.9.9";
        try {
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
            fail();
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Bundle from " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL + " already " +
                    "installed as " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_NAME + ":" +
                    OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_VERSION + " but user explicitly requested " +
                    "CatalogBundleDto{symbolicName=" + nonExistentId + ", version=" + nonExistentVersion + ", url=" +
                    OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL + "}");
        }
    }
    
    @Test(expectedExceptions = IllegalStateException.class)
    public void testUpdatingItemFails() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String id = "my.catalog.app.id.duplicate";
        addCatalogOSGiEntity(id);
        addCatalogOSGiEntity(id);
    }

    @Test
    public void testForcedUpdatingItem() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String id = "my.catalog.app.id.duplicate";
        addCatalogOSGiEntity(id);
        forceCatalogUpdate();
        addCatalogOSGiEntity(id);
        deleteCatalogEntity(id);
    }

    @Test
    public void testCreateSpecFromCatalogItem() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String id = "my.catalog.app.id.create_spec";
        addCatalogOSGiEntity(id);
        BrooklynCatalog catalog = mgmt().getCatalog();
        CatalogItem<?, ?> item = catalog.getCatalogItem(id, TEST_VERSION);
        Object spec = catalog.createSpec(item);
        Assert.assertNotNull(spec);
    }
    
    @Test
    public void testLoadResourceFromBundle() throws Exception {
        String id = "resource.test";
        addCatalogOSGiEntity(id, SIMPLE_ENTITY_TYPE);
        String yaml =
                "services: \n" +
                "  - serviceType: "+ver(id);
        Entity app = createAndStartApplication(yaml);
        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        InputStream icon = new ResourceUtils(simpleEntity).getResourceFromUrl("classpath:/brooklyn/osgi/tests/icon.gif");
        assertTrue(icon != null);
        icon.close();
    }

    private void registerAndLaunchAndAssertSimpleEntity(String symbolicName, String serviceType) throws Exception {
        addCatalogOSGiEntity(symbolicName, serviceType);
        String yaml = "name: simple-app-yaml\n" +
                      "location: localhost\n" +
                      "services: \n" +
                      "  - serviceType: "+ver(symbolicName);
        Entity app = createAndStartApplication(yaml);

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);

        deleteCatalogEntity(symbolicName);
    }

    private void addCatalogOSGiEntity(String symbolicName) {
        addCatalogOSGiEntity(symbolicName, SIMPLE_ENTITY_TYPE);
    }

    private void addCatalogOSGiEntity(String symbolicName, String serviceType) {
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + symbolicName,
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

    private void addCatalogChildOSGiEntity(String symbolicName, String serviceType) {
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + symbolicName,
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
