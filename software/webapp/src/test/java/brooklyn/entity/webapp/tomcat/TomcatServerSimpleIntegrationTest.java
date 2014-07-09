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
package brooklyn.entity.webapp.tomcat;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.net.ServerSocket;
import java.util.Iterator;

import org.jclouds.util.Throwables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.net.Networking;

import com.google.common.collect.ImmutableList;

/**
 * This tests the operation of the {@link TomcatServer} entity.
 * 
 * FIXME this test is largely superseded by WebApp*IntegrationTest which tests inter alia Tomcat
 */
public class TomcatServerSimpleIntegrationTest {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(TomcatServerSimpleIntegrationTest.class);
    
    /** don't use 8080 since that is commonly used by testing software; use different from other tests. */
    static PortRange DEFAULT_HTTP_PORT_RANGE = PortRanges.fromString("7880-7980");
    
    private TestApplication app;
    private TomcatServer tc;
    private int httpPort;
    
    @BeforeMethod(alwaysRun=true)
    public void pickFreePort() {
        for (Iterator<Integer> iter = DEFAULT_HTTP_PORT_RANGE.iterator(); iter.hasNext();) {
            Integer port = iter.next();
            if (Networking.isPortAvailable(port)) {
                httpPort = port;
                return;
            }
        }
        fail("someone is already listening on ports "+DEFAULT_HTTP_PORT_RANGE+"; tests assume that port is free on localhost");
    }
 
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups="Integration")
    public void detectFailureIfTomcatCantBindToPort() throws Exception {
        ServerSocket listener = new ServerSocket(httpPort);
        try {
            app = ApplicationBuilder.newManagedApp(TestApplication.class);
            tc = app.createAndManageChild(EntitySpec.create(TomcatServer.class).configure("httpPort", httpPort));
            
            try {
                tc.start(ImmutableList.of(app.getManagementContext().getLocationManager().manage(new LocalhostMachineProvisioningLocation())));
                fail("Should have thrown start-exception");
            } catch (Exception e) {
                // LocalhostMachineProvisioningLocation does NetworkUtils.isPortAvailable, so get -1
                IllegalArgumentException iae = Throwables2.getFirstThrowableOfType(e, IllegalArgumentException.class);
                if (iae == null || iae.getMessage() == null || !iae.getMessage().equals("port for httpPort is null")) throw e;
            } finally {
                tc.stop();
            }
            assertFalse(tc.getAttribute(TomcatServerImpl.SERVICE_UP));
        } finally {
            listener.close();
        }
    }
}
