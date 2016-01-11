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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.StringReader;
import java.util.Map;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.external.AbstractExternalConfigSupplier;
import org.apache.brooklyn.core.config.external.ExternalConfigSupplier;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.mgmt.internal.CampYamlParser;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

@Test
public class ExternalConfigYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(ExternalConfigYamlTest.class);
    
    // Choose a small jar; it is downloaded in some tests.
    // Pick an OSGi bundle that is not part of core brooklyn.
    private static final String LIBRARY_URL = "https://repository.apache.org/content/groups/public/org/apache/logging/log4j/log4j-api/2.5/log4j-api-2.5.jar";
    private static final String LIBRARY_SYMBOLIC_NAME = "org.apache.logging.log4j.api";
    private static final String LIBRARY_VERSION = "2.5.0";

    @Override
    protected LocalManagementContext newTestManagementContext() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put("brooklyn.external.myprovider", MyExternalConfigSupplier.class.getName());
        props.put("brooklyn.external.myprovider.mykey", "myval");
        props.put("brooklyn.external.myproviderWithoutMapArg", MyExternalConfigSupplierWithoutMapArg.class.getName());

        props.put("brooklyn.external.myprovider.myCatalogId", "myId");
        props.put("brooklyn.external.myprovider.myCatalogItemType", "template");
        props.put("brooklyn.external.myprovider.myCatalogVersion", "1.2");
        props.put("brooklyn.external.myprovider.myCatalogDescription", "myDescription");
        props.put("brooklyn.external.myprovider.myCatalogDisplayName", "myDisplayName");
        props.put("brooklyn.external.myprovider.myCatalogIconUrl", "classpath:///myIconUrl.png");
        props.put("brooklyn.external.myprovider.myCatalogLibraryUrl", LIBRARY_URL);
        props.put("brooklyn.external.myprovider.myCatalogLibraryName", LIBRARY_SYMBOLIC_NAME);
        props.put("brooklyn.external.myprovider.myCatalogLibraryVersion", LIBRARY_VERSION);

        return LocalManagementContextForTests.builder(true)
                .useProperties(props)
                .disableOsgi(false)
                .build();
    }

    @Test
    public void testCampYamlParserHandlesExternalisedConfig() throws Exception {
        CampYamlParser parser = mgmt().getConfig().getConfig(CampYamlParser.YAML_PARSER_KEY);
        
        DeferredSupplier<?> supplier = (DeferredSupplier<?>) parser.parse("$brooklyn:external(\"myprovider\", \"mykey\")");
        
        ExecutionContext exec = mgmt().getServerExecutionContext();
        String result = Tasks.resolveValue(supplier, String.class, exec);
        assertEquals(result, "myval");
    }

    @Test
    public void testExternalisedConfigReferencedFromYaml() throws Exception {
        ConfigKey<String> MY_CONFIG_KEY = ConfigKeys.newStringConfigKey("my.config.key");

        String yaml = Joiner.on("\n").join(
            "services:",
            "- serviceType: org.apache.brooklyn.core.test.entity.TestApplication",
            "  brooklyn.config:",
            "    my.config.key: $brooklyn:external(\"myprovider\", \"mykey\")");

        TestApplication app = (TestApplication) createAndStartApplication(new StringReader(yaml));
        waitForApplicationTasks(app);

        assertEquals(app.getConfig(MY_CONFIG_KEY), "myval");
    }

    @Test
    public void testExternalisedLocationConfigReferencedFromYaml() throws Exception {
        ConfigKey<String> MY_CONFIG_KEY = ConfigKeys.newStringConfigKey("my.config.key");

        String yaml = Joiner.on("\n").join(
            "services:",
            "- type: org.apache.brooklyn.core.test.entity.TestApplication",
            "location:",
            "  localhost:",
            "    my.config.key: $brooklyn:external(\"myprovider\", \"mykey\")");

        TestApplication app = (TestApplication) createAndStartApplication(new StringReader(yaml));
        waitForApplicationTasks(app);
        assertEquals(Iterables.getOnlyElement( app.getLocations() ).config().get(MY_CONFIG_KEY), "myval");
    }

    // Will download the given catalog library jar
    @Test(groups="Integration")
    public void testExternalisedCatalogConfigReferencedFromYaml() throws Exception {
        String yaml = Joiner.on("\n").join(
                "brooklyn.catalog:",
                "    id: $brooklyn:external(\"myprovider\", \"myCatalogId\")",
                "    itemType: $brooklyn:external(\"myprovider\", \"myCatalogItemType\")",
                "    version: $brooklyn:external(\"myprovider\", \"myCatalogVersion\")",
                "    description: $brooklyn:external(\"myprovider\", \"myCatalogDescription\")",
                "    displayName: $brooklyn:external(\"myprovider\", \"myCatalogDisplayName\")",
                "    iconUrl: $brooklyn:external(\"myprovider\", \"myCatalogIconUrl\")",
                "    brooklyn.libraries:",
                "    - $brooklyn:external(\"myprovider\", \"myCatalogLibraryUrl\")",
                "",
                "    item:",
                "      services:",
                "      - type: brooklyn.entity.database.mysql.MySqlNode");

        catalog.addItems(yaml);

        CatalogItem<Object, Object> item = Iterables.getOnlyElement(catalog.getCatalogItems());
        CatalogBundle bundle = Iterables.getOnlyElement(item.getLibraries());
        assertEquals(item.getId(), "myId:1.2");
        assertEquals(item.getCatalogItemType(), CatalogItemType.TEMPLATE);
        assertEquals(item.getVersion(), "1.2");
        assertEquals(item.getDescription(), "myDescription");
        assertEquals(item.getDisplayName(), "myDisplayName");
        assertEquals(item.getIconUrl(), "classpath:///myIconUrl.png");
        assertEquals(bundle.getUrl(), LIBRARY_URL);
    }

    // Will download the given catalog library jar
    @Test(groups="Integration")
    public void testExternalisedCatalogConfigReferencedFromYamlWithLibraryMap() throws Exception {
        String yaml = Joiner.on("\n").join(
                "brooklyn.catalog:",
                "    id: myid",
                "    itemType: template",
                "    version: 1.2",
                "    description: myDescription",
                "    displayName: myDisplayName",
                "    iconUrl: classpath:///myIconUrl.png",
                "    brooklyn.libraries:",
                "    - name: $brooklyn:external(\"myprovider\", \"myCatalogLibraryName\")",
                "      version: $brooklyn:external(\"myprovider\", \"myCatalogLibraryVersion\")",
                "      url: $brooklyn:external(\"myprovider\", \"myCatalogLibraryUrl\")",
                "",
                "    item:",
                "      services:",
                "      - type: brooklyn.entity.database.mysql.MySqlNode");

        catalog.addItems(yaml);

        CatalogItem<Object, Object> item = Iterables.getOnlyElement(catalog.getCatalogItems());
        CatalogBundle bundle = Iterables.getOnlyElement(item.getLibraries());
        assertEquals(bundle.getUrl(), LIBRARY_URL);
        assertEquals(bundle.getSymbolicName(), LIBRARY_SYMBOLIC_NAME);
        assertEquals(bundle.getVersion(), LIBRARY_VERSION);
    }

    // Will download the given catalog library jar
    // Confirms "normal" behaviour, when all values in the catalog are hard-coded rather than using external config.
    @Test(groups="Integration")
    public void testNonExternalisedCatalogConfigReferencedFromYaml() throws Exception {
        String yaml = Joiner.on("\n").join(
                "brooklyn.catalog:",
                "  id: osgi.test",
                "  itemType: template",
                "  version: 1.3",
                "  description: CentOS 6.6 With GUI - 1.3",
                "  displayName: CentOS 6.6",
                "  iconUrl: classpath:///centos.png",
                "  brooklyn.libraries:",
                "  - " + LIBRARY_URL,
                "",
                "  item:",
                "    services:",
                "    - type: brooklyn.entity.database.mysql.MySqlNode");

        catalog.addItems(yaml);

        CatalogItem<Object, Object> item = Iterables.getOnlyElement(catalog.getCatalogItems());
        assertEquals(item.getId(), "osgi.test:1.3");
        assertEquals(item.getCatalogItemType(), CatalogItemType.TEMPLATE);
        assertEquals(item.getVersion(), "1.3");
        assertEquals(item.getDescription(), "CentOS 6.6 With GUI - 1.3");
        assertEquals(item.getDisplayName(), "CentOS 6.6");
        assertEquals(item.getIconUrl(), "classpath:///centos.png");
        assertEquals(Iterables.getOnlyElement(item.getLibraries()).getUrl(), LIBRARY_URL);
    }

    @Test(groups="Integration")
    public void testExternalisedLocationConfigSetViaProvisioningPropertiesReferencedFromYaml() throws Exception {
        String yaml = Joiner.on("\n").join(
            "services:",
            "- type: "+EmptySoftwareProcess.class.getName(),
            "  provisioning.properties:",
            "    credential: $brooklyn:external(\"myprovider\", \"mykey\")",
            "location: localhost");

        Entity app = createAndStartApplication(new StringReader(yaml));
        waitForApplicationTasks(app);
        Entity entity = Iterables.getOnlyElement( app.getChildren() );
        assertEquals(Iterables.getOnlyElement( entity.getLocations() ).config().get(CloudLocationConfig.ACCESS_CREDENTIAL), "myval");
    }

    @Test
    public void testExternalisedConfigFromSupplierWithoutMapArg() throws Exception {
        ConfigKey<String> MY_CONFIG_KEY = ConfigKeys.newStringConfigKey("my.config.key");

        String yaml = Joiner.on("\n").join(
            "services:",
            "- serviceType: org.apache.brooklyn.core.test.entity.TestApplication",
            "  brooklyn.config:",
            "    my.config.key: $brooklyn:external(\"myproviderWithoutMapArg\", \"mykey\")");

        TestApplication app = (TestApplication) createAndStartApplication(new StringReader(yaml));
        waitForApplicationTasks(app);

        assertEquals(app.getConfig(MY_CONFIG_KEY), "myHardcodedVal");
    }

    @Test
    public void testWhenExternalisedConfigSupplierDoesNotExist() throws Exception {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put("brooklyn.external.myprovider", "wrong.classname.DoesNotExist");

        try {
            LocalManagementContextForTests.builder(true)
                    .useProperties(props)
                    .build();
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, ClassNotFoundException.class) == null) {
                throw e;
            }
        }
    }

    @Test
    public void testWhenExternalisedConfigSupplierDoesNotHavingRightConstructor() throws Exception {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put("brooklyn.external.myprovider", MyExternalConfigSupplierWithWrongConstructor.class.getName());

        try {
            LocalManagementContext mgmt2 = LocalManagementContextForTests.builder(true)
                    .useProperties(props)
                    .build();
            mgmt2.terminate();
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("No matching constructor")) {
                throw e;
            }
        }
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

    public static class MyExternalConfigSupplier extends AbstractExternalConfigSupplier {
        private final Map<String, String> conf;

        public MyExternalConfigSupplier(ManagementContext mgmt, String name, Map<String, String> conf) {
            super(mgmt, name);
            this.conf = conf;
        }

        @Override public String get(String key) {
            return conf.get(key);
        }
    }

    public static class MyExternalConfigSupplierWithoutMapArg extends AbstractExternalConfigSupplier {
        public MyExternalConfigSupplierWithoutMapArg(ManagementContext mgmt, String name) {
            super(mgmt, name);
        }

        @Override public String get(String key) {
            return key.equals("mykey") ? "myHardcodedVal" : null;
        }
    }

    public static class MyExternalConfigSupplierWithWrongConstructor implements ExternalConfigSupplier {
        public MyExternalConfigSupplierWithWrongConstructor(double d) {
        }

        @Override public String getName() {
            return "myname";
        }

        @Override public String get(String key) {
            return null;
        }
    }

}
