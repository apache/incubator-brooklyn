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
package org.apache.brooklyn.entity.software.base;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.policy.jclouds.os.CreateUserPolicy;
import org.apache.brooklyn.util.core.internal.ssh.SshException;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

public class CreateUserPolicyWithEntityLiveTest extends BrooklynAppLiveTestSupport {
    private JcloudsSshMachineLocation location;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Map<String, Object> config = ImmutableMap.<String, Object>builder()
                .put("imageId", "us-east-1/ami-a96b01c0")
                .put("user", "byonUser")
                .put("password", "login-secret-pass")
                .put("checkRunning.command", "echo true")
                .build();
        location = (JcloudsSshMachineLocation)((JcloudsLocation)mgmt.getLocationRegistry().resolve("jclouds:aws-ec2:us-east-1", config)).obtain();
    }

    @Test(groups = "Live", expectedExceptions = SshException.class)
    public void testBlueprintWithCreateUserPolicy() throws Throwable {
        try {
            app.createAndManageChild(EntitySpec.create(VanillaSoftwareProcess.class)
                    .configure(VanillaSoftwareProcess.INSTALL_COMMAND, "echo install command")
                    .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "echo launch command")
                    .configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "echo check running")
                    .policy(
                            PolicySpec.create(CreateUserPolicy.class)
                                    .configure("user", "newBrooklynUser")
                    ));

            app.start(ImmutableList.of(location));
        } catch(PropagatedRuntimeException e) {
            throw Exceptions.getFirstInteresting(e);
        }
    }

    @Test(groups = "Live")
    public void testBlueprintWithoutCreateUserPolicy() {
        app.createAndManageChild(EntitySpec.create(VanillaSoftwareProcess.class)
                .configure(VanillaSoftwareProcess.INSTALL_COMMAND, "echo install command")
                .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "echo launch command")
                .configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "echo check running"));

        app.start(ImmutableList.of(location));
    }
}
