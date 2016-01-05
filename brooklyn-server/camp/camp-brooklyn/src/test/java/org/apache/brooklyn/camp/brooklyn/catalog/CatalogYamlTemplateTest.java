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

import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.mgmt.osgi.OsgiStandaloneTest;
import org.apache.brooklyn.core.typereg.RegisteredTypePredicates;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.osgi.OsgiTestResources;
import org.testng.Assert;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.Test;


public class CatalogYamlTemplateTest extends AbstractYamlTest {
    
    private static final String SIMPLE_ENTITY_TYPE = OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_ENTITY;

    @Test
    public void testAddCatalogItem() throws Exception {
        RegisteredType item = makeItem();
        Assert.assertTrue(RegisteredTypePredicates.IS_APPLICATION.apply(item), "item: "+item);
        String yaml = RegisteredTypes.getImplementationDataStringForSpec(item);
        Assert.assertTrue(yaml.indexOf("sample comment")>=0,
            "YAML did not include original comments; it was:\n"+yaml);
        Assert.assertFalse(yaml.indexOf("description")>=0,
            "YAML included metadata which should have been excluded; it was:\n"+yaml);

        deleteCatalogEntity("t1");
    }

    @Test
    public void testAddCatalogItemAndCheckSource() throws Exception {
        // this will fail with the Eclipse TestNG plugin -- use the static main instead to run in eclipse!
        // see Yamls.KnownClassVersionException for details
        
        RegisteredType item = makeItem();
        String yaml = RegisteredTypes.getImplementationDataStringForSpec(item);
        Assert.assertTrue(yaml.indexOf("sample comment")>=0,
            "YAML did not include original comments; it was:\n"+yaml);
        Assert.assertFalse(yaml.indexOf("description")>=0,
            "YAML included metadata which should have been excluded; it was:\n"+yaml);

        deleteCatalogEntity("t1");
    }

    private RegisteredType makeItem() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        
        addCatalogItems(
            "brooklyn.catalog:",
            "  id: t1",
            "  item_type: template",
            "  name: My Catalog App",
            "  description: My description",
            "  icon_url: classpath://path/to/myicon.jpg",
            "  version: " + TEST_VERSION,
            "  libraries:",
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL,
            "  item:",
            "    services:",
            "    # this sample comment should be included",
            "    - type: " + SIMPLE_ENTITY_TYPE);

        return mgmt().getTypeRegistry().get("t1", TEST_VERSION);
    }

    // convenience for running in eclipse when the TestNG plugin drags in old version of snake yaml
    public static void main(String[] args) {
        TestListenerAdapter tla = new TestListenerAdapter();
        TestNG testng = new TestNG();
        testng.setTestClasses(new Class[] { CatalogYamlTemplateTest.class });
        testng.addListener(tla);
        testng.run();
    }
}
