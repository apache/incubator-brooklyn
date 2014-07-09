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
package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ManagementContext;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

public class ConnectionFailureDetectorTest {

    private static final int TIMEOUT_MS = 10*1000;
    private static final int OVERHEAD = 250;
    private static final int POLL_PERIOD = 100;

    private ManagementContext managementContext;
    private TestApplication app;
    
    private List<SensorEvent<FailureDescriptor>> events;
    
    private ServerSocket serverSocket;
    private HostAndPort serverSocketAddress;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        events = new CopyOnWriteArrayList<SensorEvent<FailureDescriptor>>();
        
        managementContext = new LocalManagementContextForTests();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        
        app.getManagementContext().getSubscriptionManager().subscribe(
                app, 
                HASensors.CONNECTION_FAILED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
        app.getManagementContext().getSubscriptionManager().subscribe(
                app, 
                HASensors.CONNECTION_RECOVERED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
        
        serverSocketAddress = startServerSocket();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        stopServerSocket();
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    private HostAndPort startServerSocket() throws Exception {
        if (serverSocketAddress != null) {
            serverSocket = new ServerSocket(serverSocketAddress.getPort());
        } else {
            for (int i = 40000; i < 40100; i++) {
                try {
                    serverSocket = new ServerSocket(i);
                } catch (IOException e) {
                    // try next port
                }
            }
            assertNotNull(serverSocket, "Failed to create server socket; no ports free in range!");
            serverSocketAddress = HostAndPort.fromParts(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort());
        }
        return serverSocketAddress;
    }

    private void stopServerSocket() throws Exception {
        if (serverSocket != null) serverSocket.close();
    }

    @Test(groups="Integration") // Has a 1 second wait
    public void testNotNotifiedOfFailuresForHealthy() throws Exception {
        // Create members before and after the policy is registered, to test both scenarios
        
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress));
        
        assertNoEventsContinually();
    }
    
    @Test
    public void testNotifiedOfFailure() throws Exception {
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress));

        stopServerSocket();

        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);
        assertEquals(events.size(), 1, "events="+events);
    }
    
    @Test
    public void testNotifiedOfRecovery() throws Exception {
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress));
        
        stopServerSocket();
        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);

        // make the connection recover
        startServerSocket();
        assertHasEventEventually(HASensors.CONNECTION_RECOVERED, Predicates.<Object>equalTo(app), null);
        assertEquals(events.size(), 2, "events="+events);
    }
    
    @Test
    public void testReportsFailureWhenAlreadyDownOnRegisteringPolicy() throws Exception {
        stopServerSocket();

        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress));

        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);
    }

    @Test(groups="Integration") // Because slow
    public void testNotNotifiedOfTemporaryFailuresDuringStabilisationDelay() throws Exception {
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress)
                .configure(ConnectionFailureDetector.CONNECTION_FAILED_STABILIZATION_DELAY, Duration.ONE_MINUTE));
        
        stopServerSocket();
        Thread.sleep(100);
        startServerSocket();

        assertNoEventsContinually();
    }
    
    @Test(groups="Integration") // Because slow
    public void testNotifiedOfFailureAfterStabilisationDelay() throws Exception {
        final int stabilisationDelay = 1000;
        
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress)
                .configure(ConnectionFailureDetector.CONNECTION_FAILED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        stopServerSocket();

        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);
    }
    
    @Test(groups="Integration") // Because slow
    public void testFailuresThenUpDownResetsStabilisationCount() throws Exception {
        final long stabilisationDelay = 1000;
        
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress)
                .configure(ConnectionFailureDetector.CONNECTION_FAILED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        stopServerSocket();
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));

        startServerSocket();
        Thread.sleep(POLL_PERIOD+OVERHEAD);
        stopServerSocket();
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        
        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);
    }
    
    @Test(groups="Integration") // Because slow
    public void testNotNotifiedOfTemporaryRecoveryDuringStabilisationDelay() throws Exception {
        final long stabilisationDelay = 1000;
        
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress)
                .configure(ConnectionFailureDetector.CONNECTION_RECOVERED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        stopServerSocket();
        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);
        events.clear();
        
        startServerSocket();
        Thread.sleep(POLL_PERIOD+OVERHEAD);
        stopServerSocket();

        assertNoEventsContinually(Duration.of(stabilisationDelay + OVERHEAD));
    }
    
    @Test(groups="Integration") // Because slow
    public void testNotifiedOfRecoveryAfterStabilisationDelay() throws Exception {
        final int stabilisationDelay = 1000;
        
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress)
                .configure(ConnectionFailureDetector.CONNECTION_RECOVERED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        stopServerSocket();
        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);
        events.clear();

        startServerSocket();
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        assertHasEventEventually(HASensors.CONNECTION_RECOVERED, Predicates.<Object>equalTo(app), null);
    }
    
    @Test(groups="Integration") // Because slow
    public void testRecoversThenDownUpResetsStabilisationCount() throws Exception {
        final long stabilisationDelay = 1000;
        
        app.addPolicy(PolicySpec.create(ConnectionFailureDetector.class)
                .configure(ConnectionFailureDetector.ENDPOINT, serverSocketAddress)
                .configure(ConnectionFailureDetector.CONNECTION_RECOVERED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        stopServerSocket();
        assertHasEventEventually(HASensors.CONNECTION_FAILED, Predicates.<Object>equalTo(app), null);
        events.clear();
        
        startServerSocket();
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        
        stopServerSocket();
        Thread.sleep(POLL_PERIOD+OVERHEAD);
        startServerSocket();
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));

        assertHasEventEventually(HASensors.CONNECTION_RECOVERED, Predicates.<Object>equalTo(app), null);
    }

    private void assertHasEvent(Sensor<?> sensor, Predicate<Object> componentPredicate, Predicate<? super CharSequence> descriptionPredicate) {
        for (SensorEvent<FailureDescriptor> event : events) {
            if (event.getSensor().equals(sensor) && 
                    (componentPredicate == null || componentPredicate.apply(event.getValue().getComponent())) &&
                    (descriptionPredicate == null || descriptionPredicate.apply(event.getValue().getDescription()))) {
                return;
            }
        }
        fail("No matching "+sensor+" event found; events="+events);
    }
    
    private void assertHasEventEventually(final Sensor<?> sensor, final Predicate<Object> componentPredicate, final Predicate<? super CharSequence> descriptionPredicate) {
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertHasEvent(sensor, componentPredicate, descriptionPredicate);
            }});
    }
    
    private void assertNoEventsContinually(Duration duration) {
        Asserts.succeedsContinually(ImmutableMap.of("timeout", duration), new Runnable() {
            @Override public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
    
    private void assertNoEventsContinually() {
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
}
