/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.nosql.couchdb;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.entity.webapp.WebAppServiceMethods;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;

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

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(String.format("http://%s:%d/_stats", getAttribute(HOSTNAME), getHttpPort()))
                .poll(new HttpPollConfig<Integer>(REQUEST_COUNT)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] { "httpd", "requests", "count" }, Integer.class))
                        .onError(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(ERROR_COUNT)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] { "httpd_status_codes", "404", "count" }, Integer.class))
                        .onError(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(TOTAL_PROCESSING_TIME)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] { "couchdb", "request_time", "count" }, Integer.class))
                        .onError(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(MAX_PROCESSING_TIME)
                        .onSuccess(HttpValueFunctions.chain(HttpValueFunctions.jsonContents(new String[] { "couchdb", "request_time", "max" }, Double.class), new Function<Double, Integer>() {
                            @Override
                            public Integer apply(@Nullable Double input) {
                                return Integer.valueOf(input.intValue());
                            }
                        }))
                        .onError(Functions.constant(-1)))
                .build();

        WebAppServiceMethods.connectWebAppServerPolicies(this);
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (httpFeed != null) httpFeed.stop();
        disconnectServiceUpIsRunning();
    }

    /** @see JavaWebAppSoftwareProcessImpl#stop() */
    @Override
    public void stop() {
        super.stop();
        // zero our workrate derived workrates.
        setAttribute(REQUESTS_PER_SECOND, 0D);
        setAttribute(AVG_REQUESTS_PER_SECOND, 0D);
    }
}
