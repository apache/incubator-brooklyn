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
package org.apache.brooklyn.entity.nosql.couchbase;

import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.sensor.feed.http.HttpFeed;
import org.apache.brooklyn.sensor.feed.http.HttpPollConfig;
import org.apache.brooklyn.sensor.feed.http.HttpValueFunctions;

import com.google.common.base.Functions;
import com.google.common.net.HostAndPort;

public class CouchbaseSyncGatewayImpl extends SoftwareProcessImpl implements CouchbaseSyncGateway {

    private HttpFeed httpFeed;

    @Override
    public Class<CouchbaseSyncGatewayDriver> getDriverInterface() {
        return CouchbaseSyncGatewayDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    protected void connectServiceUpIsRunning() {
        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this,
                getAttribute(CouchbaseSyncGateway.ADMIN_REST_API_PORT));

        String managementUri = String.format("http://%s:%s",
                hp.getHostText(), hp.getPort());

        setAttribute(MANAGEMENT_URL, managementUri);

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(200)
                .baseUri(managementUri)
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(false)))
                .build();
    }

    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    @Override
    protected void disconnectServiceUpIsRunning() {
        if (httpFeed != null) {
            httpFeed.stop();
        }
    }
    
    static {
        RendererHints.register(MANAGEMENT_URL, RendererHints.namedActionWithUrl());
    }
}