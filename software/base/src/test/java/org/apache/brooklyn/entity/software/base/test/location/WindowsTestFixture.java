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
package org.apache.brooklyn.entity.software.base.test.location;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class WindowsTestFixture {

    @SuppressWarnings("unchecked")
    public static MachineProvisioningLocation<WinRmMachineLocation> setUpWindowsLocation(ManagementContext mgmt) throws Exception {
        // Commented out / unused code included here to make it easy to supply a 
        // pre-existing Windows VM for use in a bunch of different tests.
//        return (MachineProvisioningLocation<WinRmMachineLocation>) newByonLocation((ManagementContextInternal) mgmt);
        return (MachineProvisioningLocation<WinRmMachineLocation>) newJcloudsLocation((ManagementContextInternal) mgmt);
    }
    
    private static MachineProvisioningLocation<?> newJcloudsLocation(ManagementContextInternal mgmt) {
        // Requires no userMetadata to be set, so that we use WinRmMachineLocation.getDefaultUserMetadataString()
        mgmt.getBrooklynProperties().remove("brooklyn.location.jclouds.aws-ec2.userMetadata");
        mgmt.getBrooklynProperties().remove("brooklyn.location.jclouds.userMetadata");
        mgmt.getBrooklynProperties().remove("brooklyn.location.userMetadata");
        
        return (JcloudsLocation) mgmt.getLocationRegistry().resolve("jclouds:aws-ec2:us-west-2", ImmutableMap.<String, Object>builder()
                .put("inboundPorts", ImmutableList.of(5985, 3389))
                .put("displayName", "AWS Oregon (Windows)")
                .put("imageOwner", "801119661308")
                .put("imageNameRegex", "Windows_Server-2012-R2_RTM-English-64Bit-Base-.*")
                .put("hardwareId", "m3.medium")
                .put("useJcloudsSshInit", false)
                .build());
    }
    
    @SuppressWarnings("unused")
    private static MachineProvisioningLocation<?> newByonLocation(ManagementContextInternal mgmt) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("hosts", "52.12.211.123:5985");
        config.put("osFamily", "windows");
        config.put("winrm", "52.12.211.123:5985");
        config.put("user", "Administrator");
        config.put("password", "pa55w0rd");
        config.put("useJcloudsSshInit", "false");
        config.put("byonIdentity", "123");
        return (MachineProvisioningLocation<?>) mgmt.getLocationRegistry().resolve("byon", config);
    }
}
