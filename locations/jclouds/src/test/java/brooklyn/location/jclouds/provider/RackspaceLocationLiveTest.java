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
package brooklyn.location.jclouds.provider;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.util.collections.MutableMap;

public class RackspaceLocationLiveTest extends AbstractJcloudsLocationTest {

    private static final Logger LOG = LoggerFactory.getLogger(RackspaceLocationLiveTest.class);

    private static final String PROVIDER = "rackspace-cloudservers-uk";
    private static final String REGION_NAME = null;
    private static final String IMAGE_ID = "LON/f70ed7c7-b42e-4d77-83d8-40fa29825b85"; // CentOS 6.4
    private static final String IMAGE_NAME_PATTERN = "CentOS 6.4";
    private static final String IMAGE_OWNER = null;

    public RackspaceLocationLiveTest() {
        super(PROVIDER);
    }

    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return new Object[][] {
            new Object[] { REGION_NAME, IMAGE_ID, IMAGE_OWNER }
        };
    }

    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return new Object[][] {
            new Object[] { REGION_NAME, IMAGE_NAME_PATTERN, IMAGE_OWNER }
        };
    }

    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return new Object[0][0];
    }

    @Test(groups = "Live")
    public void testVmMetadata() {
        loc = (JcloudsLocation) ctx.getLocationRegistry().resolve(PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME));
        SshMachineLocation machine = obtainMachine(MutableMap.of("imageId", IMAGE_ID, "userMetadata", MutableMap.of("mykey", "myval"), JcloudsLocation.MACHINE_CREATE_ATTEMPTS, 2));

        LOG.info("Provisioned {} vm {}; checking metadata and if ssh'able", PROVIDER, machine);

        Map<String,String> userMetadata = ((JcloudsSshMachineLocation)machine).getNode().getUserMetadata();
        assertEquals(userMetadata.get("mykey"), "myval", "metadata="+userMetadata);
        assertTrue(machine.isSshable());
    }
}
