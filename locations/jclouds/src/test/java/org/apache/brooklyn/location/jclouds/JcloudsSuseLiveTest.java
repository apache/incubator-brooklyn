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

import java.util.Map;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.stream.Streams;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Extra-special tests for deploying SUSE VMs, because we've had so many problems. For example:
 * <ul>
 *   <li>{@code groupadd -f}:  You are using an undocumented option (-f); and exits with 9
 *   <li>path does not by default contain groupadd etc (see {@link BashCommands#sbinPath()}
 * </ul>
 */
public class JcloudsSuseLiveTest extends AbstractJcloudsLiveTest {

    public static final String AWS_EC2_REGION_NAME = AWS_EC2_USEAST_REGION_NAME;
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + (AWS_EC2_REGION_NAME == null ? "" : ":" + AWS_EC2_REGION_NAME);
    public static final String AWS_IMAGE_ID = "us-east-1/ami-8dd105e6";
    
    // TODO Also requires https://github.com/jclouds/jclouds/pull/827
    @Test(groups = {"Live", "WIP"})
    protected void testSuseUsingJcloudsSshInit() throws Exception {
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        JcloudsSshMachineLocation machine = createEc2Machine(ImmutableMap.<String,Object>of(
                JcloudsLocation.USE_JCLOUDS_SSH_INIT.getName(), true,
                JcloudsLocation.USER.getName(), "myname"));
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }
    
    // TODO Also requires https://github.com/jclouds/jclouds/pull/??? (see BROOKLYN-188, for checking if group exists)
    @Test(groups = {"Live", "WIP"})
    protected void testSuseSkippingJcloudsSshInit() throws Exception {
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        JcloudsSshMachineLocation machine = createEc2Machine(ImmutableMap.<String,Object>of(
                JcloudsLocation.USE_JCLOUDS_SSH_INIT.getName(), false,
                JcloudsLocation.USER.getName(), "myname"));
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }
    
    private JcloudsSshMachineLocation createEc2Machine(Map<String,? extends Object> conf) throws Exception {
        return obtainMachine(MutableMap.<String,Object>builder()
                .putAll(conf)
                .putIfAbsent("imageId", AWS_IMAGE_ID)
                .putIfAbsent("loginUser", "ec2-user")
                .putIfAbsent("hardwareId", AWS_EC2_SMALL_HARDWARE_ID)
                .putIfAbsent("inboundPorts", ImmutableList.of(22))
                .build());
    }

    protected void assertSshable(Map<?,?> machineConfig) {
        SshMachineLocation machineWithThatConfig = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure(machineConfig));
        try {
            assertSshable(machineWithThatConfig);
        } finally {
            Streams.closeQuietly(machineWithThatConfig);
            Locations.unmanage(machineWithThatConfig);
        }
    }
}
