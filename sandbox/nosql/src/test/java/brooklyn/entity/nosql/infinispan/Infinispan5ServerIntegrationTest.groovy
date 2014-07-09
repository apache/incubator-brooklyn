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
package brooklyn.entity.nosql.infinispan

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.Entities
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplicationImpl
import brooklyn.util.internal.TimeExtras
import brooklyn.util.net.Networking
import brooklyn.util.repeat.Repeater

class Infinispan5ServerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(Infinispan5ServerIntegrationTest.class)
    
    static String DEFAULT_PROTOCOL = "memcached"
    static int DEFAULT_PORT = 11219

    static boolean portLeftOpen = false;
    
    static { TimeExtras.init() }

    @BeforeMethod(groups = [ "Integration" ])
    public void failIfPortInUse() {
        if (isPortInUse(DEFAULT_PORT, 5000L)) {
            portLeftOpen = true;
            fail "someone is already listening on port $DEFAULT_PORT; tests assume that port $DEFAULT_PORT is free on localhost"
        }
    }
 
    @AfterMethod(groups = [ "Integration" ])
    public void ensureIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;

        boolean socketClosed = new Repeater("Checking Infinispan has shut down")
            .repeat {
                    if (shutdownSocket) shutdownSocket.close();
                    try { shutdownSocket = new Socket(Networking.localHost, DEFAULT_PORT); }
                    catch (SocketException e) { gotException = e; return; }
                    gotException = null
                }
            .every(100 * MILLISECONDS)
            .until { gotException }
            .limitIterationsTo(25)
            .run();

        if (socketClosed == false) {
            logger.error "Infinispan did not shut down";
            throw new Exception("Infinispan did not shut down")
        }
    }

    public void ensureIsUp() {
        Socket socket = new Socket(Networking.localHost, DEFAULT_PORT);
        socket.close();
    }

    @Test(groups = [ "Integration", "WIP" ])
    public void testInfinispanStartsAndStops() {
        Application app = new TestApplicationImpl();
        try {
            final Infinispan5Server infini = new Infinispan5Server(parent:app)
            infini.setConfig(Infinispan5Server.PORT.getConfigKey(), DEFAULT_PORT)
            infini.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
            
            executeUntilSucceeds {
                assertTrue infini.getAttribute(Infinispan5Server.SERVICE_UP)
            }
        } finally {
            Entities.destroy(app);
        }
    }
}
