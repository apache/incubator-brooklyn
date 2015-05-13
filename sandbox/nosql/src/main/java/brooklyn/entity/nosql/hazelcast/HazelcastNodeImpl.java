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
package brooklyn.entity.nosql.hazelcast;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.access.BrooklynAccessUtils;

import com.google.common.base.Functions;
import com.google.common.net.HostAndPort;

public class HazelcastNodeImpl extends SoftwareProcessImpl implements HazelcastNode {
    
    HttpFeed httpFeed;

    @Override
    public Class<HazelcastNodeDriver> getDriverInterface() {
        return HazelcastNodeDriver.class;
    }
    
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, 5701);

        String nodeUri = String.format("http://%s:%d/hazelcast/rest/cluster", hp.getHostText(), hp.getPort());
        setAttribute(Attributes.MAIN_URI, URI.create(nodeUri));

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(1000, TimeUnit.MILLISECONDS)
                .baseUri(nodeUri)
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(false)))
                .build();
    }
    
    @Override
    protected void disconnectSensors() {
        if (httpFeed != null) {
            httpFeed.stop();
        }
    }
}
