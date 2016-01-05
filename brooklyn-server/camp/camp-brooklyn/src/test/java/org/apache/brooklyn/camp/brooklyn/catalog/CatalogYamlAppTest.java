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

import org.testng.annotations.Test;

import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;


public class CatalogYamlAppTest extends AbstractYamlTest {

    @Override
    protected LocalManagementContext newTestManagementContext() {
        // Don't need osgi
        return LocalManagementContextForTests.newInstance();
    }

    /**
     * "Contrived" example was encountered by a customer in a real use-case!
     * I couldn't yet simplify it further while still reproducing the failure.
     * Throws StackOverlfowError, without giving a nice error message about 
     * "BasicEntity" cyclic reference.
     * 
     * The circular reference comes from the member spec referencing 
     * "org.apache.brooklyn.entity.stock.BasicEntity", but that has been defined in the
     * catalog as this new blueprint (which overrides the previous value of it
     * being a reference to the Java class).
     * 
     * We need to use an id that matches something else already on the classpath.
     * Otherwise we'd get an error telling us "could not resolve item ..." when
     * attempting to add the initial catalog item.
     */
    @Test(groups="WIP") // TODO Fix this!
    public void testAddCatalogItemWithCircularReference() throws Exception {
        // Add a catalog item with a circular reference to its own id.
        addCatalogItems(
                "brooklyn.catalog:",
                "  id: org.apache.brooklyn.entity.stock.BasicEntity",
                "  version: "+TEST_VERSION,
                "services:",
                "- type: org.apache.brooklyn.entity.stock.BasicApplication",
                "  brooklyn.config:",
                "    memberSpec:",
                "      $brooklyn:entitySpec:",
                "      - type: org.apache.brooklyn.entity.stock.BasicApplication",
                "        brooklyn.children:",
                "        - type: org.apache.brooklyn.entity.stock.BasicEntity");

        try {
            // Use the blueprint from the catalog that has the circular reference.
            // This should really give a nice error (rather than a StackOverflowError!).
            addCatalogItems(
                    "brooklyn.catalog:",
                    "  id: another.app.in.the.catalog",
                    "  version: "+TEST_VERSION,
                    "services:",
                    "- type: org.apache.brooklyn.entity.stock.BasicEntity");
            deleteCatalogEntity("another.app.in.the.catalog");
        } finally {
            deleteCatalogEntity("org.apache.brooklyn.entity.stock.BasicEntity");
        }
    }

    @Test // same as above, but the minimal possible setup
    public void testAddCatalogItemWithMemberSpecCircularReference() throws Exception {
        // Add a catalog item with a circular reference to its own id through a $brooklyn:entitySpec
        addCatalogItems(
                "brooklyn.catalog:",
                "  id: org.apache.brooklyn.entity.stock.BasicApplication",
                "  version: "+TEST_VERSION,
                "services:",
                "- type: org.apache.brooklyn.entity.stock.BasicApplication",
                "  brooklyn.config:",
                "    memberSpec:",
                "      $brooklyn:entitySpec:",
                "      - type: org.apache.brooklyn.entity.stock.BasicApplication");

        try {
            // Use the blueprint from the catalog that has the circular reference.
            addCatalogItems(
                    "brooklyn.catalog:",
                    "  id: another.app.in.the.catalog",
                    "  version: "+TEST_VERSION,
                    "services:",
                    "- type: org.apache.brooklyn.entity.stock.BasicApplication");
            deleteCatalogEntity("another.app.in.the.catalog");
        } finally {
            deleteCatalogEntity("org.apache.brooklyn.entity.stock.BasicApplication");
        }
    }
}
