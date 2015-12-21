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
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.camp.brooklyn.ExternalConfigYamlTest.MyExternalConfigSupplier;
import org.apache.brooklyn.camp.brooklyn.ExternalConfigYamlTest.MyExternalConfigSupplierWithoutMapArg;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.ConfigPredicates;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.Tasks;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

@Test
public class ExternalConfigBrooklynPropertiesTest extends AbstractYamlTest {

    @Override
    protected LocalManagementContext newTestManagementContext() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put("brooklyn.external.myprovider", MyExternalConfigSupplier.class.getName());
        props.put("brooklyn.external.myprovider.mykey", "myval");
        props.put("brooklyn.external.myprovider.mykey2", "myval2");
        props.put("brooklyn.external.myproviderWithoutMapArg", MyExternalConfigSupplierWithoutMapArg.class.getName());
        props.put("myproperty", "$brooklyn:external(\"myprovider\", \"mykey\")");

        return LocalManagementContextForTests.builder(true)
                .useProperties(props)
                .build();
    }

    // Yaml parsing support is more generic than just external-config.
    // Test other parsing here, even though it's not directly related to external-config.
    @Test
    public void testYamlLiteralFromPropertiesInLocation() throws Exception {
        ((ManagementContextInternal)mgmt()).getBrooklynProperties().put(
                ConfigKeys.newStringConfigKey("myDynamicProperty"), "$brooklyn:literal(\"myliteral\")");
        
        String val = mgmt().getConfig().getConfig(ConfigKeys.newStringConfigKey("myDynamicProperty"));
        assertEquals(val, "myliteral");
    }

    @Test
    public void testInvalidYamlExpression() throws Exception {
        ((ManagementContextInternal)mgmt()).getBrooklynProperties().put(
                ConfigKeys.newStringConfigKey("myInvalidExternal"), "$brooklyn:external");
        
        try {
            String val = mgmt().getConfig().getConfig(ConfigKeys.newStringConfigKey("myInvalidExternal"));
            Asserts.shouldHaveFailedPreviously("val="+val);
        } catch (IllegalArgumentException e) {
            Asserts.expectedFailureContains(e, "Error evaluating node");
        }
    }

    @Test
    public void testExternalisedConfigFromPropertiesInLocation() throws Exception {
        BrooklynProperties props = ((ManagementContextInternal)mgmt()).getBrooklynProperties();
        props.put("brooklyn.location.jclouds.aws-ec2.identity", "$brooklyn:external(\"myprovider\", \"mykey\")");
        props.put("brooklyn.location.jclouds.aws-ec2.credential", "$brooklyn:external(\"myprovider\", \"mykey2\")");
        
        JcloudsLocation loc = (JcloudsLocation) mgmt().getLocationRegistry().resolve("jclouds:aws-ec2:us-east-1");
        assertEquals(loc.getIdentity(), "myval");
        assertEquals(loc.getCredential(), "myval2");
    }

    @Test
    public void testExternalisedConfigInProperties() throws Exception {
        runExternalisedConfigGetters("myproperty", "myval");
    }
    
    @Test
    public void testExternalisedConfigInAddedStringProperty() throws Exception {
        ((ManagementContextInternal)mgmt()).getBrooklynProperties().put(
                "myDynamicProperty", "$brooklyn:external(\"myprovider\", \"mykey\")");
        runExternalisedConfigGetters("myDynamicProperty", "myval");
    }
    
    @Test
    public void testExternalisedConfigInAddedKeyProperty() throws Exception {
        ((ManagementContextInternal)mgmt()).getBrooklynProperties().put(
                ConfigKeys.newStringConfigKey("myDynamicProperty"), "$brooklyn:external(\"myprovider\", \"mykey\")");
        runExternalisedConfigGetters("myDynamicProperty", "myval");
    }
    
    @Test
    public void testExternalisedConfigInAddedMapProperty() throws Exception {
        ((ManagementContextInternal)mgmt()).getBrooklynProperties().addFromMap(
                ImmutableMap.of("myDynamicProperty", "$brooklyn:external(\"myprovider\", \"mykey\")"));
        runExternalisedConfigGetters("myDynamicProperty", "myval");
    }

    protected void runExternalisedConfigGetters(String property, String expectedVal) throws Exception {
        runExternalisedConfigGetters(((ManagementContextInternal)mgmt()).getBrooklynProperties(), property, expectedVal, true);
    }
    
    protected void runExternalisedConfigGetters(BrooklynProperties props, String property, String expectedVal, boolean testSubMap) throws Exception {
        ExecutionContext exec = mgmt().getServerExecutionContext();

        String val1 = props.getConfig(ConfigKeys.newStringConfigKey(property));
        assertEquals(val1, expectedVal);
        
        DeferredSupplier<?> val2 = (DeferredSupplier<?>) props.getRawConfig(ConfigKeys.newStringConfigKey(property));
        assertEquals(Tasks.resolveValue(val2, String.class, exec), expectedVal);
        
        DeferredSupplier<?> val3 = (DeferredSupplier<?>) props.getConfigRaw(ConfigKeys.newStringConfigKey(property), false).get();
        assertEquals(Tasks.resolveValue(val3, String.class, exec), expectedVal);

        DeferredSupplier<?> val4 = (DeferredSupplier<?>) props.getAllConfig().get(ConfigKeys.newStringConfigKey(property));
        assertEquals(Tasks.resolveValue(val4, String.class, exec), expectedVal);
        
        String val5 = props.getFirst(property);
        assertTrue(val5.startsWith("$brooklyn:external"), "val="+val5);
        
        if (testSubMap) {
            BrooklynProperties submap = props.submap(ConfigPredicates.nameEqualTo(property));
            runExternalisedConfigGetters(submap, property, expectedVal, false);
        }
    }
}
