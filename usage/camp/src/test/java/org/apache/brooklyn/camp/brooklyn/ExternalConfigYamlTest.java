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

import com.google.common.collect.Iterables;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.external.AbstractExternalConfigSupplier;
import org.apache.brooklyn.core.config.external.ExternalConfigSupplier;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;

@Test
public class ExternalConfigYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(ExternalConfigYamlTest.class);

    @Override
    protected LocalManagementContext newTestManagementContext() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put("brooklyn.external.myprovider", MyExternalConfigSupplier.class.getName());
        props.put("brooklyn.external.myprovider.mykey", "myval");
        props.put("brooklyn.external.myproviderWithoutMapArg", MyExternalConfigSupplierWithoutMapArg.class.getName());

        return LocalManagementContextForTests.builder(true)
                .useProperties(props)
                .build();
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
