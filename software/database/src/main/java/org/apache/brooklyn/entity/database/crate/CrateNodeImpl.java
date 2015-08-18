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
package org.apache.brooklyn.entity.database.crate;

import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.util.guava.Functionals;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaAppUtils;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.jmx.JmxFeed;

public class CrateNodeImpl extends SoftwareProcessImpl implements CrateNode{

    private JmxFeed jmxFeed;
    private HttpFeed httpFeed;

    static {
        JavaAppUtils.init();
        RendererHints.register(MANAGEMENT_URL, RendererHints.namedActionWithUrl());
    }

    @Override
    public Class getDriverInterface() {
        return CrateNodeDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
        jmxFeed = JavaAppUtils.connectMXBeanSensors(this);
        setAttribute(DATASTORE_URL, "crate://" + getAttribute(HOSTNAME) + ":" + getPort());
        String url = "http://" + getAttribute(HOSTNAME) + ":" + getHttpPort();
        setAttribute(MANAGEMENT_URL, url);

        httpFeed = HttpFeed.builder()
                .entity(this)
                .baseUri(url)
                .poll(new HttpPollConfig<String>(SERVER_NAME)
                        .onSuccess(HttpValueFunctions.jsonContents("name", String.class)))
                .poll(new HttpPollConfig<Integer>(SERVER_STATUS)
                        .onSuccess(HttpValueFunctions.jsonContents("status", Integer.class)))
                .poll(new HttpPollConfig<Boolean>(SERVER_OK)
                        .onSuccess(HttpValueFunctions.jsonContents("ok", Boolean.class)))
                .poll(new HttpPollConfig<String>(SERVER_BUILD_TIMESTAMP)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[]{"version", "build_timestamp"}, String.class)))
                .poll(new HttpPollConfig<String>(SERVER_BUILD_HASH)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[]{"version", "build_hash"}, String.class)))
                .poll(new HttpPollConfig<Boolean>(SERVER_IS_BUILD_SNAPSHOT)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] {"version", "build_snapshot"}, Boolean.class)))
                .poll(new HttpPollConfig<String>(SERVER_LUCENE_VERSION)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] {"version", "lucene_version"}, String.class)))
                .poll(new HttpPollConfig<String>(SERVER_ES_VERSION)
                        .onSuccess(HttpValueFunctions.jsonContents(new String[] {"version", "es_version"}, String.class)))
                .build();

        addEnricher(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                .from(SERVER_OK)
                .computing(Functionals.ifNotEquals(true).value("Crate server reports it is not ok."))
                .build());
    }

    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
        if (httpFeed != null) httpFeed.stop();
        super.disconnectSensors();
    }

    public Integer getPort() {
        return getAttribute(CRATE_PORT);
    }

    public Integer getHttpPort() {
        return getAttribute(CRATE_HTTP_PORT);
    }

}
