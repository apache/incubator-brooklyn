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
package org.apache.brooklyn.entity.webapp.nodejs;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.net.ServerSocket;
import java.util.Iterator;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.jclouds.util.Throwables2;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.util.net.Networking;

import com.google.common.collect.ImmutableList;

/**
 * This tests the operation of the {@link NodeJsWebAppService} entity.
 */
public class NodeJsWebAppSimpleIntegrationTest extends BrooklynAppLiveTestSupport {

    private static PortRange DEFAULT_PORT_RANGE = PortRanges.fromString("3000-3099");

    private int httpPort;

    @BeforeMethod(alwaysRun=true)
    public void pickFreePort() {
        for (Iterator<Integer> iter = DEFAULT_PORT_RANGE.iterator(); iter.hasNext();) {
            Integer port = iter.next();
            if (Networking.isPortAvailable(port)) {
                httpPort = port;
                return;
            }
        }
        fail("someone is already listening on ports "+DEFAULT_PORT_RANGE+"; tests assume that port is free on localhost");
    }

    @Test(groups="Integration")
    public void detectFailureIfNodeJsBindToPort() throws Exception {
        ServerSocket listener = new ServerSocket(httpPort);
        try {
            LocalhostMachineProvisioningLocation loc = app.newLocalhostProvisioningLocation();
            NodeJsWebAppService nodejs = app.createAndManageChild(EntitySpec.create(NodeJsWebAppService.class).configure("httpPort", httpPort));
            try {
                nodejs.start(ImmutableList.of(loc));
                fail("Should have thrown start-exception");
            } catch (Exception e) {
                // LocalhostMachineProvisioningLocation does NetworkUtils.isPortAvailable, so get -1
                IllegalArgumentException iae = Throwables2.getFirstThrowableOfType(e, IllegalArgumentException.class);
                if (iae == null || iae.getMessage() == null || !iae.getMessage().equals("port for http is null")) throw e;
            } finally {
                nodejs.stop();
            }
            assertFalse(nodejs.getAttribute(NodeJsWebAppServiceImpl.SERVICE_UP));
        } finally {
            listener.close();
        }
    }
}
