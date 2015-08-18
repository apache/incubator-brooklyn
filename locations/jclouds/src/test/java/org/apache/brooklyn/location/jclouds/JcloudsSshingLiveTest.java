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

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

/**
 * Tests the initial ssh command execution (e.g. user creation), using jclouds TemplateOptions
 * and using just brooklyn.
 */
public class JcloudsSshingLiveTest extends AbstractJcloudsLiveTest {

    public static final String SOFTLAYER_REGION_NAME = SOFTLAYER_AMS01_REGION_NAME;
    public static final String SOTLAYER_LOCATION_SPEC = "jclouds:" + SOFTLAYER_PROVIDER + (SOFTLAYER_REGION_NAME == null ? "" : ":" + SOFTLAYER_REGION_NAME);
    
    protected JcloudsSshMachineLocation machine;
    
    @Test(groups = {"Live"})
    public void testCreatesUserUsingJcloudsTemplateOptions() throws Exception {
        runCreatesUser(true);
    }
    
    @Test(groups = {"Live"})
    public void testCreatesUserWithoutUsingJcloudsTemplateOptions() throws Exception {
        runCreatesUser(false);
    }
    
    protected void runCreatesUser(boolean useJcloudsSshInit) throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USE_JCLOUDS_SSH_INIT.getName(), Boolean.toString(useJcloudsSshInit));
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(SOTLAYER_LOCATION_SPEC);
        
        JcloudsSshMachineLocation machine = obtainMachine(MutableMap.<String,Object>builder()
                .putIfAbsent("inboundPorts", ImmutableList.of(22))
                .build());
        assertSshable(machine);
        assertEquals(machine.getUser(), "myname");
    }
}
