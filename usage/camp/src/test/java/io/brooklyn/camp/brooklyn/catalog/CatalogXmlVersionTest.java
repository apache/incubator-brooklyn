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

import io.brooklyn.camp.brooklyn.AbstractYamlTest;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;

public class CatalogXmlVersionTest extends AbstractYamlTest {
    @Override
    protected LocalManagementContext newTestManagementContext() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL, "classpath://simple-catalog.xml");
        return LocalManagementContextForTests.newInstance(properties);
    }

    @DataProvider(name = "types")
    public Object[][] createTypes() {
        return new Object[][] {
                {"brooklyn.entity.basic.BasicApplication"},
                {"brooklyn.entity.basic.BasicApplication:0.0.0.SNAPSHOT"},
                {"brooklyn.entity.basic.BasicApplication:2.0"},
                {"BasicApp"},
                {"BasicApp:0.0.0.SNAPSHOT"},
                {"BasicApp:2.0"}
        };
    }

    @Test(dataProvider = "types")
    public void testXmlCatalogItem(String type) throws Exception {
        String yaml = "name: simple-app-yaml\n" +
                "location: localhost\n" +
                "services: \n" +
                "  - type: " + type;
        createAndStartApplication(yaml);
    }
}
