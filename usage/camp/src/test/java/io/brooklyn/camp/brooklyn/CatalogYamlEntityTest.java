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

import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.management.osgi.OsgiStandaloneTest;

import com.google.common.collect.Iterables;


public class CatalogYamlEntityTest extends AbstractYamlTest {
    private static final String SIMPLE_ENTITY_TYPE = "brooklyn.osgi.tests.SimpleEntity";

    @Test
    public void testAddCatalogItem() throws Exception {
        String registeredTypeName = "my.catalog.app.id.load";
        addCatalogOSGiEntity(registeredTypeName);

        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(registeredTypeName);
        assertEquals(item.getRegisteredTypeName(), registeredTypeName);

        deleteCatalogEntity(registeredTypeName);
    }

    @Test
    public void testLaunchApplicationReferencingCatalog() throws Exception {
        String registeredTypeName = "my.catalog.app.id.launch";
        registerAndLaunchAndAssertSimpleEntity(registeredTypeName, SIMPLE_ENTITY_TYPE);
    }

    @Test
    public void testLaunchApplicationWithCatalogReferencingOtherCatalog() throws Exception {
        String referencedRegisteredTypeName = "my.catalog.app.id.referenced";
        String referrerRegisteredTypeName = "my.catalog.app.id.referring";
        addCatalogOSGiEntity(referencedRegisteredTypeName, SIMPLE_ENTITY_TYPE);
        addCatalogOSGiEntity(referrerRegisteredTypeName, referencedRegisteredTypeName);

        String yaml = "name: simple-app-yaml\n" +
                      "location: localhost\n" +
                      "services: \n" +
                      "  - serviceType: "+referrerRegisteredTypeName;
        Entity app = createAndStartApplication(yaml);

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);

        deleteCatalogEntity(referencedRegisteredTypeName);
        deleteCatalogEntity(referrerRegisteredTypeName);
    }

    @Test
    public void testLaunchApplicationChildWithCatalogReferencingOtherCatalog() throws Exception {
        String referencedRegisteredTypeName = "my.catalog.app.id.child.referenced";
        String referrerRegisteredTypeName = "my.catalog.app.id.child.referring";
        addCatalogOSGiEntity(referencedRegisteredTypeName, SIMPLE_ENTITY_TYPE);
        addCatalogChildOSGiEntity(referrerRegisteredTypeName, referencedRegisteredTypeName);

        Entity app = createAndStartApplication(
            "name: simple-app-yaml",
            "location: localhost",
            "services:",
            "- serviceType: "+BasicEntity.class.getName(),
            "  brooklyn.children:",
            "  - type: " + referrerRegisteredTypeName);

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

        deleteCatalogEntity(referencedRegisteredTypeName);
        deleteCatalogEntity(referrerRegisteredTypeName);
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
        registerAndLaunchFailsWithRecursionError(registeredTypeName, registeredTypeName);
    }
    
    @Test
    public void testLaunchApplicationChildLoopCatalogIdFails() throws Exception {
        String referrerRegisteredTypeName = "my.catalog.app.id.child.referring";
        addCatalogChildOSGiEntity(referrerRegisteredTypeName, referrerRegisteredTypeName);

        try {
            createAndStartApplication(
                "name: simple-app-yaml",
                "location: localhost",
                "services:",
                "- serviceType: "+BasicEntity.class.getName(),
                "  brooklyn.children:",
                "  - type: " + referrerRegisteredTypeName);

                fail("Expected to throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Recursive reference to "+referrerRegisteredTypeName));
        }

        deleteCatalogEntity(referrerRegisteredTypeName);
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
                "",
                "services:",
                "- type: " + SIMPLE_ENTITY_TYPE);

        addCatalogItem(
                "brooklyn.catalog:",
                "  id: " + parentCatalogId,
                "  libraries:",
                "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
                "",
                "services:",
                "- type: " + childCatalogId);

        try {
            createAndStartApplication(
                    "services:",
                    "- type: " + parentCatalogId);
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().endsWith("cannot be matched"));
            assertTrue(e.getMessage().contains(SIMPLE_ENTITY_TYPE));
        }

        deleteCatalogEntity(parentCatalogId);
        deleteCatalogEntity(childCatalogId);
    }

    private void registerAndLaunchAndAssertSimpleEntity(String registeredTypeName, String serviceType) throws Exception {
        addCatalogOSGiEntity(registeredTypeName, serviceType);
        String yaml = "name: simple-app-yaml\n" +
                      "location: localhost\n" +
                      "services: \n" +
                      "  - serviceType: "+registeredTypeName;
        Entity app = createAndStartApplication(yaml);

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);

        deleteCatalogEntity(registeredTypeName);
    }

    private void registerAndLaunchFailsWithRecursionError(String registeredTypeName, String serviceType) throws Exception {
        addCatalogOSGiEntity(registeredTypeName, serviceType);
        String yaml = "name: simple-app-yaml\n" +
                      "location: localhost\n" +
                      "services: \n" +
                      "  - serviceType: "+registeredTypeName;
        try {
            createAndStartApplication(yaml);
            fail("Expected to throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Recursive reference to "+registeredTypeName));
        }

        deleteCatalogEntity(registeredTypeName);
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
            "  version: 0.1.2",
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
            "  version: 0.1.2",
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: " + BasicEntity.class.getName(),
            "  brooklyn.children:",
            "  - type: " + serviceType);
    }

}
