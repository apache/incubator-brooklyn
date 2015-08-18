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
package org.apache.brooklyn.entity.nosql.couchdb;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import org.apache.brooklyn.entity.webapp.WebAppServiceMethods;
import org.apache.brooklyn.sensor.feed.http.HttpFeed;
import org.apache.brooklyn.sensor.feed.http.HttpPollConfig;
import org.apache.brooklyn.sensor.feed.http.HttpValueFunctions;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Functionals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;

import com.google.common.base.Function;
import com.google.common.base.Functions;

/**
 * Implementation of {@link CouchDBNode}.
 */
public class CouchDBNodeImpl extends SoftwareProcessImpl implements CouchDBNode {

    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeImpl.class);

    public CouchDBNodeImpl() {
    }

    public Integer getHttpPort() { return getAttribute(CouchDBNode.HTTP_PORT); }
    public Integer getHttpsPort() { return getAttribute(CouchDBNode.HTTPS_PORT); }
    public String getClusterName() { return getAttribute(CouchDBNode.CLUSTER_NAME); }

    @Override
    public Class<CouchDBNodeDriver> getDriverInterface() {
        return CouchDBNodeDriver.class;
    }

    private volatile HttpFeed httpFeed;

    @Override 
    protected void connectSensors() {
        super.connectSensors();

        connectServiceUpIsRunning();

        boolean retrieveUsageMetrics = getConfig(RETRIEVE_USAGE_METRICS);
        
        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(String.format("http://%s:%d/_stats", getAttribute(HOSTNAME), getHttpPort()))
                .poll(new HttpPollConfig<Integer>(REQUEST_COUNT)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] { "httpd", "requests", "count" }, Integer.class))
                        .onFailureOrException(Functions.constant(-1))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Integer>(ERROR_COUNT)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] { "httpd_status_codes", "404", "count" }, Integer.class))
                        .onFailureOrException(Functions.constant(-1))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Integer>(TOTAL_PROCESSING_TIME)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] { "couchdb", "request_time", "count" }, Integer.class))
                        .onFailureOrException(Functions.constant(-1))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Integer>(MAX_PROCESSING_TIME)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(new String[] { "couchdb", "request_time", "max" }, Double.class), TypeCoercions.function(Integer.class)))
                        .onFailureOrException(Functions.constant(-1))
                        .enabled(retrieveUsageMetrics))
                .build();

        WebAppServiceMethods.connectWebAppServerPolicies(this);
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (httpFeed != null) httpFeed.stop();
        disconnectServiceUpIsRunning();
    }

    /** @see JavaWebAppSoftwareProcessImpl#postStop() */
    @Override
    protected void postStop() {
        super.postStop();
        // zero our workrate derived workrates.
        setAttribute(REQUESTS_PER_SECOND_LAST, 0D);
        setAttribute(REQUESTS_PER_SECOND_IN_WINDOW, 0D);
    }
}
