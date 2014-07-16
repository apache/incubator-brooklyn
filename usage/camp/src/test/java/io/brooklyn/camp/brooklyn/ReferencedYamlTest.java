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

import java.util.Collection;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.management.osgi.OsgiStandaloneTest;

import com.google.common.collect.Iterables;

public class ReferencedYamlTest extends AbstractYamlTest {
    
    @Test
    public void testReferenceEntityYamlAsPlatformComponent() throws Exception {
        String entityName = "Reference child name";
        Entity app = createAndStartApplication(
            "services:",
            "- name: " + entityName,
            "  type: classpath://yaml-ref-entity.yaml");
        
        checkChildEntitySpec(app, entityName);
    }

    @Test
    public void testAnonymousReferenceEntityYamlAsPlatformComponent() throws Exception {
        Entity app = createAndStartApplication(
            "services:",
            "- type: classpath://yaml-ref-entity.yaml");
        
        checkChildEntitySpec(app, "service");
    }

    @Test
    public void testReferenceAppYamlAsPlatformComponent() throws Exception {
        Entity app = createAndStartApplication(
            "services:",
            "- name: Reference child name",
            "  type: classpath://yaml-ref-app.yaml");
        
        Assert.assertEquals(app.getChildren().size(), 0);
        Assert.assertEquals(app.getDisplayName(), "Reference child name");

        //child is a proxy so equality test won't do
        Assert.assertEquals(app.getEntityType().getName(), BasicApplication.class.getName());
    }

    @Test
    public void testReferenceYamlAsChild() throws Exception {
        String entityName = "Reference child name";
        Entity createAndStartApplication = createAndStartApplication(
            "services:",
            "- type: brooklyn.entity.basic.BasicEntity",
            "  brooklyn.children:",
            "  - name: " + entityName,
            "    type: classpath://yaml-ref-entity.yaml");
        
        checkGrandchildEntitySpec(createAndStartApplication, entityName);
    }

    @Test
    public void testAnonymousReferenceYamlAsChild() throws Exception {
        Entity createAndStartApplication = createAndStartApplication(
            "services:",
            "- type: brooklyn.entity.basic.BasicEntity",
            "  brooklyn.children:",
            "  - type: classpath://yaml-ref-entity.yaml");
        
        checkGrandchildEntitySpec(createAndStartApplication, "service");
    }

    @Test
    public void testCatalogReferencingYamlUrl() throws Exception {
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: yaml.reference",
            "services:",
            "- type: classpath://yaml-ref-entity.yaml");
        
        String entityName = "YAML -> catalog item -> yaml url";
        Entity app = createAndStartApplication(
            "services:",
            "- name: " + entityName,
            "  type: yaml.reference");
        
        checkChildEntitySpec(app, entityName);
    }

    @Test
    public void testYamlUrlReferencingCatalog() throws Exception {
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: yaml.basic",
            "services:",
            "- type: brooklyn.entity.basic.BasicEntity");
        
        String entityName = "YAML -> yaml url -> catalog item";
        Entity app = createAndStartApplication(
            "services:",
            "- name: " + entityName,
            "  type: classpath://yaml-ref-catalog.yaml");
        
        checkChildEntitySpec(app, entityName);
    }

    /**
     * Tests that a YAML referenced by URL from a catalog item
     * will have access to the catalog item's bundles.
     */
    @Test
    public void testCatalogLeaksBundlesToReferencedYaml() throws Exception {
        String parentCatalogId = "my.catalog.app.id.url.parent";
        addCatalogItem(
            "brooklyn.catalog:",
            "  id: " + parentCatalogId,
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: classpath://yaml-ref-bundle-without-libraries.yaml");

        Entity app = createAndStartApplication(
            "services:",
                "- type: " + parentCatalogId);
        
        Collection<Entity> children = app.getChildren();
        Assert.assertEquals(children.size(), 1);
        Entity child = Iterables.getOnlyElement(children);
        Assert.assertEquals(child.getEntityType().getName(), "brooklyn.osgi.tests.SimpleEntity");

        deleteCatalogEntity(parentCatalogId);
    }

    private void checkChildEntitySpec(Entity app, String entityName) {
        Collection<Entity> children = app.getChildren();
        Assert.assertEquals(children.size(), 1);
        Entity child = Iterables.getOnlyElement(children);
        Assert.assertEquals(child.getDisplayName(), entityName);
        Assert.assertEquals(child.getEntityType().getName(), BasicEntity.class.getName());
    }

    private void checkGrandchildEntitySpec(Entity createAndStartApplication, String entityName) {
        Collection<Entity> children = createAndStartApplication.getChildren();
        Assert.assertEquals(children.size(), 1);
        Entity child = Iterables.getOnlyElement(children);
        Collection<Entity> grandChildren = child.getChildren();
        Assert.assertEquals(grandChildren.size(), 1);
        Entity grandChild = Iterables.getOnlyElement(grandChildren);
        Assert.assertEquals(grandChild.getDisplayName(), entityName);
        Assert.assertEquals(grandChild.getEntityType().getName(), BasicEntity.class.getName());
    }
    
}
