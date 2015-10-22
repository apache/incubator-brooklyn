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
package org.apache.brooklyn.entity.nosql.infinispan;

import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.test.entity.TestApplicationImpl;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.groovy.TimeExtras;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class Infinispan5ServerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(Infinispan5ServerIntegrationTest.class);
    
    static String DEFAULT_PROTOCOL = "memcached";
    static int DEFAULT_PORT = 11219;

    static boolean portLeftOpen = false;
    
    static { TimeExtras.init(); }

    @BeforeMethod(groups = "Integration")
    public void failIfPortInUse() {
        if (Networking.isPortAvailable(DEFAULT_PORT)) {
            portLeftOpen = true;
            fail("someone is already listening on port $DEFAULT_PORT; tests assume that port $DEFAULT_PORT is free on localhost");
        }
    }
 
    @AfterMethod(groups = "Integration")
    public void ensureIsShutDown() {
        boolean socketClosed = new Repeater("Checking Infinispan has shut down")
            .every(Duration.millis(100))
            .until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    Socket shutdownSocket = null;

                    try { shutdownSocket = new Socket(Networking.getLocalHost(), DEFAULT_PORT); }
                    catch (SocketException e) { return true; }
                    shutdownSocket.close();

                    return false;
                }
            })
            .limitIterationsTo(25)
            .run();

        if (socketClosed == false) {
            logger.error("Infinispan did not shut down");
            throw new IllegalStateException("Infinispan did not shut down");
        }
    }

    public void ensureIsUp() throws IOException {
        Socket socket = new Socket(Networking.getLocalHost(), DEFAULT_PORT);
        socket.close();
    }

    @Test(groups = {"Integration", "WIP"})
    public void testInfinispanStartsAndStops() {
        Application app = new TestApplicationImpl();
        try {
            final Infinispan5Server infini = new Infinispan5Server(ImmutableMap.of("parent", app));
            infini.config().set(Infinispan5Server.PORT.getConfigKey(), PortRanges.fromInteger(DEFAULT_PORT));
            infini.start(ImmutableList.of(new LocalhostMachineProvisioningLocation(ImmutableMap.of("name","london"))));
            EntityTestUtils.assertAttributeEqualsEventually(infini, Infinispan5Server.SERVICE_UP, Boolean.TRUE);
        } finally {
            Entities.destroy(app);
        }
    }
}
