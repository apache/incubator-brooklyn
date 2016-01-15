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
package org.apache.brooklyn.location.jclouds;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.ec2.domain.BlockDeviceMapping;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class JcloudsLocationTemplateOptionsCustomisersLiveTest extends AbstractJcloudsLiveTest {

    private static final String LOCATION_SPEC = AWS_EC2_PROVIDER + ":" + AWS_EC2_USEAST_REGION_NAME;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        jcloudsLocation = resolve(LOCATION_SPEC);
    }

    // Doesn't actually do much with the cloud, but jclouds requires identity and credential before it will work
    @Test(groups = "Live")
    public void testGeneralPurposeTemplateOptionCustomisation() throws Exception {
        ConfigKey<Map<String, Object>> key = JcloudsLocationConfig.TEMPLATE_OPTIONS;

        ConfigBag config = ConfigBag.newInstance()
                .configure(key, ImmutableMap.of("iamInstanceProfileName", (Object)"helloworld"));
        AWSEC2TemplateOptions templateOptions = jcloudsLocation.getComputeService().templateOptions().as(AWSEC2TemplateOptions.class);

        invokeCustomizeTemplateOptions(templateOptions, JcloudsLocationConfig.TEMPLATE_OPTIONS, config);

        assertEquals(templateOptions.getIAMInstanceProfileName(), "helloworld");
    }

    // Doesn't actually do much with the cloud, but jclouds requires identity and credential before it will work
    @Test(groups = "Live")
    public void testGeneralPurposeTemplateOptionCustomisationWithList() throws Exception {
        ConfigKey<Map<String, Object>> key = JcloudsLocationConfig.TEMPLATE_OPTIONS;

        ConfigBag config = ConfigBag.newInstance()
                        .configure(key, ImmutableMap.of(
                                "iamInstanceProfileName", (Object) "helloworld",
                                "mapNewVolumeToDeviceName", (Object) ImmutableList.of("/dev/sda1/", 123, true)));
        AWSEC2TemplateOptions templateOptions = jcloudsLocation.getComputeService().templateOptions().as(AWSEC2TemplateOptions.class);

        invokeCustomizeTemplateOptions(templateOptions, JcloudsLocationConfig.TEMPLATE_OPTIONS, config);

        assertEquals(templateOptions.getIAMInstanceProfileName(), "helloworld");
        assertEquals(templateOptions.getBlockDeviceMappings().size(), 1);
        BlockDeviceMapping blockDeviceMapping = templateOptions.getBlockDeviceMappings().iterator().next();
        assertEquals(blockDeviceMapping.getDeviceName(), "/dev/sda1/");
        assertEquals(blockDeviceMapping.getEbsVolumeSize(), (Integer)123);
        assertTrue(blockDeviceMapping.getEbsDeleteOnTermination());
    }

    /**
     * Invoke a specific template options customizer on a TemplateOptions instance.
     *
     * @param templateOptions the TemplateOptions instance that you expect the customizer to modify.
     * @param keyToTest the config key that identifies the customizer. This must be present in both @{code locationConfig} and @{link JcloudsLocation.SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES}.
     * @param locationConfig simulated configuration for the location. This must contain at least an entry for @{code keyToTest}.
     */
    private void invokeCustomizeTemplateOptions(TemplateOptions templateOptions, ConfigKey<?> keyToTest, ConfigBag locationConfig) {
        checkNotNull(templateOptions, "templateOptions");
        checkNotNull(keyToTest, "keyToTest");
        checkNotNull(locationConfig, "locationConfig");
        checkState(JcloudsLocation.SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.containsKey(keyToTest),
                "SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES does not contain a customiser for the key " + keyToTest.getName());
        checkState(locationConfig.containsKey(keyToTest),
                "location config does not contain the key " + keyToTest.getName());

        JcloudsLocation.CustomizeTemplateOptions code = JcloudsLocation.SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.get(keyToTest);
        code.apply(templateOptions, locationConfig, locationConfig.get(keyToTest));
    }

    private JcloudsLocation resolve(String spec) {
        return (JcloudsLocation) managementContext.getLocationRegistry().resolve("jclouds:"+spec);
    }
}
