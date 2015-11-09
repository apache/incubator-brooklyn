/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.brooklyn.util.net;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.net.ServerSocket;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class ReachableSocketFinderTest {

    private static final Logger LOG = LoggerFactory.getLogger(ReachableSocketFinderTest.class);

    private HostAndPort socket1;
    private HostAndPort socket2;
    private Map<HostAndPort, Boolean> reachabilityResults;
    private ListeningExecutorService executor;
    private Predicate<HostAndPort> socketTester;
    private ReachableSocketFinder finder;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        socket1 = HostAndPort.fromParts("1.1.1.1", 1111);
        socket2 = HostAndPort.fromParts("1.1.1.2", 1112);
        reachabilityResults = Maps.newConcurrentMap();
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        socketTester = new Predicate<HostAndPort>() {
            @Override public boolean apply(HostAndPort input) {
                return Boolean.TRUE.equals(reachabilityResults.get(input));
            }
        };
        
        finder = new ReachableSocketFinder(socketTester, executor);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
    }
    
    @Test(expectedExceptions=IllegalStateException.class)
    public void testWhenNoSocketsThrowsIllegalState() throws Exception {
        finder.findOpenSocketOnNode(ImmutableList.<HostAndPort>of(), Duration.TEN_SECONDS);
    }
    
    @Test
    public void testReturnsReachableSocket() throws Exception {
        reachabilityResults.put(socket1, true);
        reachabilityResults.put(socket2, false);
        assertEquals(finder.findOpenSocketOnNode(ImmutableList.<HostAndPort>of(socket1, socket2), Duration.TEN_SECONDS), socket1);
        
        reachabilityResults.put(socket1, false);
        reachabilityResults.put(socket2, true);
        assertEquals(finder.findOpenSocketOnNode(ImmutableList.<HostAndPort>of(socket1, socket2), Duration.TEN_SECONDS), socket2);
    }
    
    @Test
    public void testPollsUntilPortReachable() throws Exception {
        reachabilityResults.put(socket1, false);
        reachabilityResults.put(socket2, false);
        final ListenableFuture<HostAndPort> future = executor.submit(new Callable<HostAndPort>() {
                @Override public HostAndPort call() throws Exception {
                    return finder.findOpenSocketOnNode(ImmutableList.<HostAndPort>of(socket1, socket2), Duration.TEN_SECONDS);
                }});

        // Should keep trying
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertFalse(future.isDone());
            }});

        // When port is reached, it completes
        reachabilityResults.put(socket1, true);
        assertEquals(future.get(30, TimeUnit.SECONDS), socket1);
    }
    
    // Mark as integration, as can't rely (in Apache infra) for a port to stay unused during test!
    @Test(groups="Integration")
    public void testReturnsRealReachableSocket() throws Exception {
        ReachableSocketFinder realFinder = new ReachableSocketFinder(executor);
        ServerSocket socket = connectToPort();
        try {
            HostAndPort addr = HostAndPort.fromParts(socket.getInetAddress().getHostAddress(), socket.getLocalPort());
            HostAndPort wrongAddr = HostAndPort.fromParts(socket.getInetAddress().getHostAddress(), findAvailablePort());
            
            assertEquals(realFinder.findOpenSocketOnNode(ImmutableList.of(addr, wrongAddr), Duration.ONE_MINUTE), addr);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    // Mark as integration, as can't rely (in Apache infra) for a port to stay unused during test!
    // And slow test - takes 5 seconds.
    @Test(groups="Integration")
    public void testFailsIfRealSocketUnreachable() throws Exception {
        ReachableSocketFinder realFinder = new ReachableSocketFinder(executor);
        HostAndPort wrongAddr = HostAndPort.fromParts(Networking.getLocalHost().getHostAddress(), findAvailablePort());
        
        try {
            HostAndPort result = realFinder.findOpenSocketOnNode(ImmutableList.of(wrongAddr), Duration.FIVE_SECONDS);
            fail("Expected failure, but got "+result);
        } catch (NoSuchElementException e) {
            // success
        }
    }

    private ServerSocket connectToPort() throws Exception {
        ServerSocket result = new ServerSocket(0);
        LOG.info("Acquired port "+result+" for test "+JavaClassNames.niceClassAndMethod());
        return result;
    }
    
    private int findAvailablePort() throws Exception {
        final int startPort = 58767;
        final int endPort = 60000;
        int port = startPort;
        do {
            if (Networking.isPortAvailable(port)) {
                return port;
            }
            port++;
            // repeat until we can get a port
        } while (port <= endPort);
        throw new Exception("could not get a port in range "+startPort+"-"+endPort);
    }
}
