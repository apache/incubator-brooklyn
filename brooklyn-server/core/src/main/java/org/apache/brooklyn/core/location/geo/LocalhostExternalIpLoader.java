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
package org.apache.brooklyn.core.location.geo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Durations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class LocalhostExternalIpLoader {

    public static final Logger LOG = LoggerFactory.getLogger(LocalhostExternalIpLoader.class);

    /**
     * Mutex to guard access to retrievingLocalExternalIp.
     */
    private static final Object mutex = new Object();
    /**
     * When null there is no ongoing attempt to load the external IP address. Either no attempt has been made or the
     * last attempt has been completed.
     * When set there is an ongoing attempt to load the external IP address. New attempts to lookup the external IP
     * address should wait on this latch instead of making another attempt to load the IP address.
     */
    private static CountDownLatch retrievingLocalExternalIp;
    /**
     * Cached external IP address of localhost. Null if either no attempt has been made to resolve the address or the
     * last attempt failed.
     */
    private static volatile String localExternalIp;

    private static class IpLoader implements Callable<String> {
        private static final Pattern ipPattern = Pattern.compile(
                "\\b((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b");
        final String url;

        protected IpLoader(String url) {
            this.url = url;
        }

        @Override
        public String call() {
            String response = ResourceUtils.create(LocalhostExternalIpLoader.class)
                    .getResourceAsString(url).trim();
            return postProcessResponse(response);
        }

        String postProcessResponse(String response) {
            Matcher matcher = ipPattern.matcher(response);
            boolean matched = matcher.find();
            if (!matched) {
                LOG.error("No IP address matched in output from {}: {}", url, response);
                return null;
            } else {
                return matcher.group();
            }
        }
    }

    @VisibleForTesting
    static List<String> getIpAddressWebsites() {
        String file = new ResourceUtils(LocalhostExternalIpLoader.class)
                .getResourceAsString("classpath://org/apache/brooklyn/location/geo/external-ip-address-resolvers.txt");
        Iterable<String> lines = Splitter.on('\n')
                .omitEmptyStrings()
                .trimResults()
                .split(file);
        List<String> urls = Lists.newArrayList(Iterables.filter(lines, Predicates.not(StringPredicates.startsWith("#"))));
        Collections.shuffle(urls);
        return urls;
    }

    @VisibleForTesting
    static String getIpAddressFrom(String url) {
        return new IpLoader(url).call();
    }
    
    /** As {@link #getLocalhostIpWithin(Duration)} but returning 127.0.0.1 if not accessible */
    public static String getLocalhostIpQuicklyOrDefault() {
        String result = doLoad(Duration.seconds(2));
        if (result==null) return "127.0.0.1";
        return result;
    }

    /** As {@link #getLocalhostIpWithin(Duration)} but without the time limit cut-off, failing if the load gives an error. */
    public static String getLocalhostIpWaiting() {
        return getLocalhostIpWithin(null);
    }

    /**
     * Attempts to load the public IP address of localhost, failing if the load
     * does not complete within the given duration.
     * @return The public IP address of localhost
     */
    public static String getLocalhostIpWithin(Duration timeout) {
        String result = doLoad(timeout);
        if (result == null) {
            throw new IllegalStateException("Unable to retrieve external IP for localhost; network may be down or slow or remote service otherwise not responding");
        }
        return result;
    }

    /**
     * Requests URLs returned by {@link #getIpAddressWebsites()} until one returns an IP address or all URLs have been tried.
     * The address is assumed to be the external IP address of localhost.
     * @param blockFor The maximum duration to wait for the IP address to be resolved.
     *                 An indefinite way if null.
     * @return A string in IPv4 format, or null if no such address could be ascertained.
     */
    private static String doLoad(Duration blockFor) {
        // Check for a cached external IP address
        final String resolvedIp = localExternalIp;
        if (resolvedIp != null) {
            return resolvedIp;
        }

        // Check for an ongoing attempt to load an external IP address
        final boolean startAttemptToLoadIp;
        final CountDownLatch attemptToRetrieveLocalExternalIp;
        synchronized (mutex) {
            if (retrievingLocalExternalIp == null) {
                retrievingLocalExternalIp = new CountDownLatch(1);
                startAttemptToLoadIp = true;
            }
            else {
                startAttemptToLoadIp = false;
            }
            attemptToRetrieveLocalExternalIp = retrievingLocalExternalIp;
        }

        // Attempt to load the external IP address in private thread, otherwise blocks for 30s+ on dodgy network!
        // (we can skip it if someone else is doing it, we have synch lock so we'll get notified)
        if (startAttemptToLoadIp) {
            final List<String> candidateUrls = getIpAddressWebsites();
            if (candidateUrls.isEmpty()) {
                LOG.debug("No candidate URLs to use to determine external IP of localhost");
                return null;
            }

            new Thread() {
                public void run() {
                    for (String url : candidateUrls) {
                        try {
                            LOG.debug("Looking up external IP of this host from {} in private thread {}", url, Thread.currentThread());
                            final String loadedIp = new IpLoader(url).call();
                            localExternalIp = loadedIp;
                            LOG.debug("Finished looking up external IP of this host from {} in private thread, result {}", url, loadedIp);
                            break;
                        } catch (Throwable t) {
                            LOG.debug("Unable to look up external IP of this host from {}, probably offline {})", url, t);
                        }
                    }

                    attemptToRetrieveLocalExternalIp.countDown();

                    synchronized (mutex) {
                        retrievingLocalExternalIp = null;
                    }
                }
            }.start();
        }

        try {
            if (blockFor!=null) {
                Durations.await(attemptToRetrieveLocalExternalIp, blockFor);
            } else {
                attemptToRetrieveLocalExternalIp.await();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }

        return localExternalIp;
    }

}
