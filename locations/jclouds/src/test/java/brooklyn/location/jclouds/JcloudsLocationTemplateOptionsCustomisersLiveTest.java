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
package brooklyn.location.jclouds;

import brooklyn.config.ConfigKey;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Identifiers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions;
import org.jclouds.compute.options.TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.testng.Assert.assertEquals;

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
        ConfigKey<Map<String, String>> key = JcloudsLocationConfig.TEMPLATE_OPTIONS;

        ConfigBag config = ConfigBag.newInstance()
                .configure(key, ImmutableMap.of("iamInstanceProfileName", "helloworld"));
        AWSEC2TemplateOptions templateOptions = jcloudsLocation.getComputeService().templateOptions().as(AWSEC2TemplateOptions.class);

        invokeCustomizeTemplateOptions(templateOptions, JcloudsLocationConfig.TEMPLATE_OPTIONS, config);

        assertEquals(templateOptions.getIAMInstanceProfileName(), "helloworld");
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
