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
package org.apache.brooklyn.camp.brooklyn;

import java.util.Collection;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.mgmt.osgi.OsgiStandaloneTest;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.testng.Assert;
import org.testng.annotations.Test;

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
        
        checkChildEntitySpec(app, "Basic entity");
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
            "- type: org.apache.brooklyn.entity.stock.BasicEntity",
            "  brooklyn.children:",
            "  - name: " + entityName,
            "    type: classpath://yaml-ref-entity.yaml");
        
        checkGrandchildEntitySpec(createAndStartApplication, entityName);
    }

    @Test
    public void testAnonymousReferenceYamlAsChild() throws Exception {
        Entity createAndStartApplication = createAndStartApplication(
            "services:",
            "- type: org.apache.brooklyn.entity.stock.BasicEntity",
            "  brooklyn.children:",
            "  - type: classpath://yaml-ref-entity.yaml");
        
        checkGrandchildEntitySpec(createAndStartApplication, "Basic entity");
    }

    @Test
    public void testCatalogReferencingYamlUrl() throws Exception {
        addCatalogItems(
            "brooklyn.catalog:",
            "  id: yaml.reference",
            "  version: " + TEST_VERSION,
            "services:",
            "- type: classpath://yaml-ref-entity.yaml");
        
        String entityName = "YAML -> catalog item -> yaml url";
        Entity app = createAndStartApplication(
            "services:",
            "- name: " + entityName,
            "  type: " + ver("yaml.reference"));
        
        checkChildEntitySpec(app, entityName);
    }

    @Test
    public void testYamlUrlReferencingCatalog() throws Exception {
        addCatalogItems(
            "brooklyn.catalog:",
            "  id: yaml.basic",
            "  version: " + TEST_VERSION,
            "services:",
            "- type: org.apache.brooklyn.entity.stock.BasicEntity");
        
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
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String parentCatalogId = "my.catalog.app.id.url.parent";
        addCatalogItems(
            "brooklyn.catalog:",
            "  id: " + parentCatalogId,
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "",
            "services:",
            "- type: classpath://yaml-ref-bundle-without-libraries.yaml");

        Entity app = createAndStartApplication(
            "services:",
                "- type: " + ver(parentCatalogId));
        
        Collection<Entity> children = app.getChildren();
        Assert.assertEquals(children.size(), 1);
        Entity child = Iterables.getOnlyElement(children);
        Assert.assertEquals(child.getEntityType().getName(), "org.apache.brooklyn.test.osgi.entities.SimpleEntity");

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
