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

import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.policy.Policy;

import com.google.common.collect.Iterables;

public class CatalogYamlPolicyTest extends AbstractYamlTest {
    private static final String SIMPLE_POLICY_TYPE = "brooklyn.osgi.tests.SimplePolicy";
    private static final String SIMPLE_ENTITY_TYPE = "brooklyn.osgi.tests.SimpleEntity";

    @Test
    public void testAddCatalogItem() throws Exception {
        assertEquals(countCatalogPolicies(), 0);

        String registeredTypeName = "my.catalog.policy.id.load";
        addCatalogOSGiPolicy(registeredTypeName, SIMPLE_POLICY_TYPE);

        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(registeredTypeName);
        assertEquals(item.getRegisteredTypeName(), registeredTypeName);
        assertEquals(countCatalogPolicies(), 1);

        deleteCatalogEntity(registeredTypeName);
    }

    @Test
    public void testLaunchApplicationReferencingPolicy() throws Exception {
        String registeredTypeName = "my.catalog.policy.id.launch";
        addCatalogOSGiPolicy(registeredTypeName, SIMPLE_POLICY_TYPE);
        Entity app = createAndStartApplication(
            "name: simple-app-yaml",
            "location: localhost",
            "services: ",
            "  - type: brooklyn.entity.basic.BasicEntity\n" +
            "    brooklyn.policies:\n" +
            "    - type: " + registeredTypeName,
            "      brooklyn.config:",
            "        config2: config2 override",
            "        config3: config3");

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        Policy policy = Iterables.getOnlyElement(simpleEntity.getPolicies());
        assertEquals(policy.getPolicyType().getName(), SIMPLE_POLICY_TYPE);
        assertEquals(policy.getConfig(new BasicConfigKey<String>(String.class, "config1")), "config1");
        assertEquals(policy.getConfig(new BasicConfigKey<String>(String.class, "config2")), "config2 override");
        assertEquals(policy.getConfig(new BasicConfigKey<String>(String.class, "config3")), "config3");

        deleteCatalogEntity(registeredTypeName);
    }

    @Test
    public void testLaunchApplicationWithCatalogReferencingOtherCatalog() throws Exception {
        String referencedRegisteredTypeName = "my.catalog.policy.id.referenced";
        String referrerRegisteredTypeName = "my.catalog.policy.id.referring";
        addCatalogOSGiPolicy(referencedRegisteredTypeName, SIMPLE_POLICY_TYPE);

        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + referrerRegisteredTypeName,
            "  name: My Catalog App",
            "  description: My description",
            "  icon_url: classpath://path/to/myicon.jpg",
            "  version: 0.1.2",
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: " + SIMPLE_ENTITY_TYPE,
            "  brooklyn.policies:",
            "  - type: " + referencedRegisteredTypeName);

        String yaml = "name: simple-app-yaml\n" +
                      "location: localhost\n" +
                      "services: \n" +
                      "  - serviceType: "+referrerRegisteredTypeName;

        Entity app = createAndStartApplication(yaml);

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        Policy policy = Iterables.getOnlyElement(simpleEntity.getPolicies());
        assertEquals(policy.getPolicyType().getName(), SIMPLE_POLICY_TYPE);

        deleteCatalogEntity(referencedRegisteredTypeName);
    }

    @Test
    public void testParentCatalogDoesNotLeakBundlesToChildCatalogItems() throws Exception {
        String childCatalogId = "my.catalog.policy.id.no_bundles";
        String parentCatalogId = "my.catalog.policy.id.parent";
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + childCatalogId,
            "",
            "services:",
            "- type: " + BasicEntity.class.getName(),
            "  brooklyn.policies:",
            "  - type: " + SIMPLE_POLICY_TYPE);

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
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Unable to load " + SIMPLE_POLICY_TYPE));
        }

        deleteCatalogEntity(parentCatalogId);
        deleteCatalogEntity(childCatalogId);
    }

    private void addCatalogOSGiPolicy(String registeredTypeName, String serviceType) {
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + registeredTypeName,
            "  name: My Catalog Policy",
            "  description: My description",
            "  icon_url: classpath://path/to/myicon.jpg",
            "  version: 0.1.2",
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "brooklyn.policies:",
            "- type: " + serviceType,
            "  brooklyn.config:",
            "    config1: config1",
            "    config2: config2");
    }

    private int countCatalogPolicies() {
        return Iterables.size(mgmt().getCatalog().getCatalogItems(CatalogPredicates.IS_POLICY));
    }

}
