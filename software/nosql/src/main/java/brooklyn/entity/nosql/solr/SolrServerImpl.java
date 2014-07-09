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
package brooklyn.entity.nosql.solr;

import java.util.concurrent.TimeUnit;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;

import com.google.common.base.Functions;

/**
 * Implementation of {@link SolrServer}.
 */
public class SolrServerImpl extends SoftwareProcessImpl implements SolrServer {

    @Override
    public Integer getSolrPort() {
        return getAttribute(SolrServer.SOLR_PORT);
    }

    @Override
    public Class<SolrServerDriver> getDriverInterface() {
        return SolrServerDriver.class;
    }

    private volatile HttpFeed httpFeed;

    @Override 
    protected void connectSensors() {
        super.connectSensors();

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(String.format("http://%s:%d/solr", getAttribute(HOSTNAME), getSolrPort()))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(false)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();

        if (httpFeed != null) httpFeed.stop();
    }
}
