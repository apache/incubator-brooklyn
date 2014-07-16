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

import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.management.osgi.OsgiStandaloneTest;

import com.google.common.collect.Iterables;


public class CatalogYamlTest extends AbstractYamlTest {
    private static final String SIMPLE_ENTITY_TYPE = "brooklyn.osgi.tests.SimpleEntity";

    @Test
    public void testAddCatalogItem() throws Exception {
        String registeredTypeName = "my.catalog.app.id.load";
        addCatalogOSGiEntity(registeredTypeName);
        
        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(registeredTypeName);
        assertEquals(item.getRegisteredTypeName(), registeredTypeName);
        
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

    private void registerAndLaunchAndAssertSimpleEntity(String registeredTypeName, String serviceType) throws Exception {
        addCatalogOSGiEntity(registeredTypeName, serviceType);
        try {
            String yaml = "name: simple-app-yaml\n" +
                          "location: localhost\n" +
                          "services: \n" +
                          "  - serviceType: "+registeredTypeName;
            Entity app = createAndStartApplication(yaml);
    
            Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
            assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);
        } finally {
            deleteCatalogEntity(registeredTypeName);
        }
    }

    private void registerAndLaunchFailsWithRecursionError(String registeredTypeName, String serviceType) throws Exception {
        addCatalogOSGiEntity(registeredTypeName, serviceType);
        try {
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
        } finally {
            deleteCatalogEntity(registeredTypeName);
        }
    }

    private void addCatalogOSGiEntity(String registeredTypeName) {
        addCatalogOSGiEntity(registeredTypeName, SIMPLE_ENTITY_TYPE);
    }
    
    private void addCatalogOSGiEntity(String registeredTypeName, String serviceType) {
        String catalogYaml =
            "name: "+registeredTypeName+"\n"+
            // FIXME name above should be unnecessary -- slight problem somewhere currently
            // as testListApplicationYaml fails without the line above
            "brooklyn.catalog:\n"+
            "  id: " + registeredTypeName + "\n"+
            "  name: My Catalog App\n"+
            "  description: My description\n"+
            "  icon_url: classpath://path/to/myicon.jpg\n"+
            "  version: 0.1.2\n"+
            "  libraries:\n"+
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL + "\n"+
            "\n"+
            "services:\n"+
            "- type: " + serviceType;

        addCatalogItem(catalogYaml);
    }

}
