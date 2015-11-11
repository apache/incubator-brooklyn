/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.framework;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@inheritDoc}
 */
public class TestHttpCallImpl extends AbstractTest implements TestHttpCall {

    private static final Logger LOG = LoggerFactory.getLogger(TestHttpCallImpl.class);

    /**
     * {@inheritDoc}
     */
    public void start(Collection<? extends Location> locations) {
        if (!getChildren().isEmpty()) {
            throw new RuntimeException(String.format("The entity [%s] cannot have child entities", getClass().getName()));
        }
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        final String url = getConfig(TARGET_URL);
        final List<Map<String, Object>> assertions = getConfig(ASSERTIONS);
        final Duration timeout = getConfig(TIMEOUT);
        final HttpAssertionTarget httpAssertionTarget = getConfig(ASSERTION_TARGET);
        try {
            TestFrameworkAssertions.checkAssertions(buildDataSupplier(httpAssertionTarget, url),
                    ImmutableMap.of("timeout", timeout), assertions);
            sensors().set(SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Throwable t) {
            LOG.info("Url [{}] test failed", url);
            sensors().set(SERVICE_UP, false);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    private Supplier<String> buildDataSupplier(final HttpAssertionTarget httpAssertionTarget, final String url) {

        switch (httpAssertionTarget) {
            case body:
                return new Supplier<String>() {
                    @Override
                    public String get() {
                        return HttpTool.getContent(url);
                    }
                };
            case status:
                return new Supplier<String>() {
                    @Override
                    public String get() {
                        try {
                            return String.valueOf(HttpTool.getHttpStatusCode(url));
                        } catch (Exception e) {
                            LOG.error("HTTP call to [{}] failed due to [{}] ... returning Status code [0 - Unreachable]", url, e.getMessage());
                            return "0";
                        }

                    }
                };
            default:
                throw new RuntimeException("Unable to build a data supplier to target assertion [" + httpAssertionTarget + "]");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        sensors().set(SERVICE_UP, false);
    }

    /**
     * {@inheritDoc}
     */
    public void restart() {
        final Collection<Location> locations = Lists.newArrayList(getLocations());
        stop();
        start(locations);
    }

}