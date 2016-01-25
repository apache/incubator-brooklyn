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
package org.apache.brooklyn.test;

import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

public class NetworkingTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkingTestUtils.class);

    public static void assertPortsAvailableEventually(final Map<String, Integer> ports) {
        // If we get into a TCP TIMED-WAIT state, it could take 4 minutes for the port to come available.
        // Could that be causing our integration tests to fail sometimes when run in the suite?!
        // Let's wait for the required ports in setup, rather than running+failing the test.
        assertPortsAvailableEventually(ports, Duration.minutes(4));
    }
    
    public static void assertPortsAvailableEventually(final Map<String, Integer> ports, final Duration timeout) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", Duration.minutes(4)), new Runnable() {
            private boolean logged = false;
            public void run() {
                try {
                    assertPortsAvailable(ports);
                } catch (Throwable t) {
                    if (!logged) {
                        LOG.warn("Port(s) not available; waiting for up to "+timeout+" ("+Exceptions.getFirstInteresting(t)+")");
                        logged = true;
                    }
                    throw Exceptions.propagate(t);
                }
            }});
        LOG.debug("Ports are available: "+ports);
    }
    
    public static void assertPortsAvailable(final Map<String, Integer> ports) {
        for (Map.Entry<String, Integer> entry : ports.entrySet()) {
            String errmsg = "port "+entry.getValue()+" not available for "+entry.getKey();
            assertTrue(Networking.isPortAvailable(entry.getValue()), errmsg);
        }
    }
    
    /** Returns a port not in use somewhere around the seed;
     * this is not a foolproof way to prevent collisions, 
     * but strikes a good balance of traceability (different callers will use different ranges)
     * and collision avoidance, esp when combined with <code>Localhost...obtain(thisResult+"+");</code>.
     */
    @Beta
    public static int randomPortAround(int seed) {
        return Networking.nextAvailablePort( seed + (int)Math.floor(Math.random() * 1000) );
    }
}
