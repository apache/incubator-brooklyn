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

import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.Entity;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CatalogXmlVersionTest extends AbstractCatalogXmlTest {

    public CatalogXmlVersionTest(String catalogUrl) {
        super("classpath://simple-catalog.xml");
    }

    @DataProvider(name = "types")
    public Object[][] createTypes() {
        return new Object[][] {
                {"org.apache.brooklyn.entity.stock.BasicApplication"},
                {"org.apache.brooklyn.entity.stock.BasicApplication:0.0.0.SNAPSHOT"},
                {"org.apache.brooklyn.entity.stock.BasicApplication:2.0"},
                {"BasicApp"}, // test that items with symbolicName not matching the type work
                {"BasicApp:0.0.0.SNAPSHOT"},
                {"BasicApp:2.0"},
                {"org.apache.brooklyn.test.osgi.entities.SimpleApplication"}, //test that classpath is used
        };
    }

    @Test(dataProvider = "types")
    public void testXmlCatalogItem(String type) throws Exception {
        startApp(type);
    }

    @Test
    public void testJavaPrefixDoesNotLoadXMLCatalogItem() throws Exception {
        Entity entity = startApp("java:org.apache.brooklyn.camp.brooklyn.catalog.TestBasicApp");
        assertTrue(entity instanceof TestBasicApp, "Entity is not a " + TestBasicApp.class.getName() + ", instead the type is " + entity.getEntityType().getName());
    }

}
