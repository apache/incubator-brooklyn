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
package brooklyn.util.internal.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;

/**
 * Allow replayable request to be retried a limited number of times, and impose an exponential back-off
 * delay before returning.
 * <p>
 * Copied and modified from jclouds; original author was James Murty
 */
public class BackoffLimitedRetryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BackoffLimitedRetryHandler.class);

    private final int retryCountLimit;

    private final long delayStart;

    public BackoffLimitedRetryHandler() {
        this(5, 50L);
    }
    
    public BackoffLimitedRetryHandler(int retryCountLimit, long delayStart) {
        this.retryCountLimit = retryCountLimit;
        this.delayStart = delayStart;
    }
    
    public void imposeBackoffExponentialDelay(int failureCount, String commandDescription) {
        imposeBackoffExponentialDelay(delayStart, 2, failureCount, retryCountLimit, commandDescription);
    }

    public void imposeBackoffExponentialDelay(long period, int pow, int failureCount, int max, String commandDescription) {
        imposeBackoffExponentialDelay(period, period * 10l, pow, failureCount, max, commandDescription);
    }

    public void imposeBackoffExponentialDelay(long period,
            long maxPeriod,
            int pow,
            int failureCount,
            int max,
            String commandDescription) {
        long delayMs = (long) (period * Math.pow(failureCount, pow));
        delayMs = (delayMs > maxPeriod) ? maxPeriod : delayMs;
        if (LOG.isDebugEnabled()) LOG.debug("Retry {}/{}: delaying for {} ms: {}", 
                new Object[] {failureCount, max, delayMs, commandDescription});
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Exceptions.propagate(e);
        }
    }

}
