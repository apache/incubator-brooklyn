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
package org.apache.brooklyn.entity.nosql.solr;

import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;

import com.google.common.base.Functions;
import com.google.common.net.HostAndPort;

import java.net.URI;
import java.util.concurrent.TimeUnit;

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

        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, getSolrPort());

        String solrUri = String.format("http://%s:%d/solr", hp.getHostText(), hp.getPort());
        setAttribute(Attributes.MAIN_URI, URI.create(solrUri));

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(solrUri)
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
