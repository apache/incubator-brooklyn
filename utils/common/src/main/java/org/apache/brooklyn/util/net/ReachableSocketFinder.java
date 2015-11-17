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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.RuntimeInterruptedException;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * For finding an open/reachable ip:port for a node.
 */
public class ReachableSocketFinder {

    private static final Logger LOG = LoggerFactory.getLogger(ReachableSocketFinder.class);

    private final Predicate<HostAndPort> socketTester;
    private final ListeningExecutorService userExecutor;

    public ReachableSocketFinder(ListeningExecutorService userExecutor) {
        this(
                new Predicate<HostAndPort>() {
                    @Override public boolean apply(HostAndPort input) {
                        return Networking.isReachable(input);
                    }}, 
                userExecutor);
    }

    public ReachableSocketFinder(Predicate<HostAndPort> socketTester, ListeningExecutorService userExecutor) {
        this.socketTester = checkNotNull(socketTester, "socketTester");
        this.userExecutor = checkNotNull(userExecutor, "userExecutor");
    }

    /**
     * 
     * @param sockets The host-and-ports to test
     * @param timeout Max time to try to connect to the ip:port
     * 
     * @return The reachable ip:port
     * @throws NoSuchElementException If no ports accessible within the given time
     * @throws NullPointerException  If the sockets or duration is null
     * @throws IllegalStateException  If the sockets to test is empty
     */
    public HostAndPort findOpenSocketOnNode(final Collection<? extends HostAndPort> sockets, Duration timeout) {
        checkNotNull(sockets, "sockets");
        checkState(sockets.size() > 0, "No hostAndPort sockets supplied");
        
        LOG.debug("blocking on any reachable socket in {} for {}", sockets, timeout);

        final AtomicReference<HostAndPort> result = new AtomicReference<HostAndPort>();
        boolean passed = Repeater.create("socket-reachable")
                .limitTimeTo(timeout)
                .backoffTo(Duration.FIVE_SECONDS)
                .until(new Callable<Boolean>() {
                        public Boolean call() {
                            Optional<HostAndPort> reachableSocket = tryReachable(sockets, Duration.seconds(2));
                            if (reachableSocket.isPresent()) {
                                result.compareAndSet(null, reachableSocket.get());
                                return true;
                            }
                            return false;
                        }})
                .run();

        if (passed) {
            LOG.debug("<< socket {} opened", result);
            assert result.get() != null;
            return result.get();
        } else {
            LOG.warn("No sockets in {} reachable after {}", sockets, timeout);
            throw new NoSuchElementException("could not connect to any socket in " + sockets);
        }
    }

    /**
     * Checks if any any of the given HostAndPorts are reachable. It checks them all concurrently, and
     * returns the first that is reachable (or Optional.absent).
     */
    private Optional<HostAndPort> tryReachable(Collection<? extends HostAndPort> sockets, Duration timeout) {
        final AtomicReference<HostAndPort> reachableSocket = new AtomicReference<HostAndPort>();
        final CountDownLatch latch = new CountDownLatch(1);
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        for (final HostAndPort socket : sockets) {
            futures.add(userExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (socketTester.apply(socket)) {
                                reachableSocket.compareAndSet(null, socket);
                                latch.countDown();
                            }
                        } catch (RuntimeInterruptedException e) {
                            throw e;
                        } catch (RuntimeException e) {
                            LOG.warn("Error checking reachability of ip:port "+socket, e);
                        }
                    }}));
        }
        
        ListenableFuture<List<Object>> compoundFuture = Futures.successfulAsList(futures);
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            while (reachableSocket.get() == null && !compoundFuture.isDone() && timeout.isLongerThan(stopwatch)) {
                latch.await(50, TimeUnit.MILLISECONDS);
            }            
            return Optional.fromNullable(reachableSocket.get());
            
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            for (Future<?> future : futures) {
                future.cancel(true);
            }
        }
    }
}
