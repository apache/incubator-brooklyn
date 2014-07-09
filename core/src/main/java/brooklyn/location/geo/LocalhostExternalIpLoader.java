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
package brooklyn.location.geo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Durations;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

public class LocalhostExternalIpLoader {

    public static final Logger LOG = LoggerFactory.getLogger(LocalhostExternalIpLoader.class);

    private static final AtomicBoolean retrievingLocalExternalIp = new AtomicBoolean(false);
    private static final CountDownLatch triedLocalExternalIp = new CountDownLatch(1);
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
                .getResourceAsString("classpath://brooklyn/location/geo/external-ip-address-resolvers.txt");
        List<String> urls = Lists.newArrayList(Splitter.on('\n')
                .omitEmptyStrings()
                .trimResults()
                .split(file));
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
     * Requests URLs returned by {@link #getIpAddressWebsites()} until one returns an IP address.
     * The address is assumed to be the external IP address of localhost.
     * @param blockFor The maximum duration to wait for the IP address to be resolved.
     *                 An indefinite way if null.
     * @return A string in IPv4 format, or null if no such address could be ascertained.
     */
    private static String doLoad(Duration blockFor) {
        if (localExternalIp != null) {
            return localExternalIp;
        }

        final List<String> candidateUrls = getIpAddressWebsites();
        if (candidateUrls.isEmpty()) {
            LOG.debug("No candidate URLs to use to determine external IP of localhost");
            return null;
        }

        // do in private thread, otherwise blocks for 30s+ on dodgy network!
        // (we can skip it if someone else is doing it, we have synch lock so we'll get notified)
        if (retrievingLocalExternalIp.compareAndSet(false, true)) {
            new Thread() {
                public void run() {
                    for (String url : candidateUrls) {
                        try {
                            LOG.debug("Looking up external IP of this host from {} in private thread {}", url, Thread.currentThread());
                            localExternalIp = new IpLoader(url).call();
                            LOG.debug("Finished looking up external IP of this host from {} in private thread, result {}", url, localExternalIp);
                            break;
                        } catch (Throwable t) {
                            LOG.debug("Unable to look up external IP of this host from {}, probably offline {})", url, t);
                        } finally {
                            retrievingLocalExternalIp.set(false);
                            triedLocalExternalIp.countDown();
                        }
                    }
                }
            }.start();
        }

        try {
            if (blockFor!=null) {
                Durations.await(triedLocalExternalIp, blockFor);
            } else {
                triedLocalExternalIp.await();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        if (localExternalIp == null) {
            return null;
        }
        return localExternalIp;
    }

}
