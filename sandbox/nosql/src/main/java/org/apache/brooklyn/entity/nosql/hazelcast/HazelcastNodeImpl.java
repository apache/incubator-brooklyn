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
package org.apache.brooklyn.entity.nosql.hazelcast;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Functions;
import com.google.common.net.HostAndPort;

public class HazelcastNodeImpl extends SoftwareProcessImpl implements HazelcastNode {
    
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastNodeImpl.class);
    
    HttpFeed httpFeed;

    @Override
    public Class<HazelcastNodeDriver> getDriverInterface() {
        return HazelcastNodeDriver.class;
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Connecting sensors for node: {} ", getAttribute(Attributes.HOSTNAME));
        }
        
        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, getNodePort());

        String nodeUri = String.format("http://%s:%d/hazelcast/rest/cluster", hp.getHostText(), hp.getPort());
        sensors().set(Attributes.MAIN_URI, URI.create(nodeUri));

        if (LOG.isDebugEnabled()) {
            LOG.debug("Node {} is using {} as a main URI", this, nodeUri);
        }
        
        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(3000, TimeUnit.MILLISECONDS)
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

        if (LOG.isDebugEnabled()) {
            LOG.debug("Disconnecting sensors for node: {} ", getAttribute(Attributes.HOSTNAME));
        }
        
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }


    @Override
    public String getGroupName() {
        return getConfig(HazelcastNode.GROUP_NAME);
    }

    @Override
    public String getGroupPassword() {
        return getConfig(HazelcastNode.GROUP_PASSWORD);
    }

    @Override
    public String getNodeName() {
        return getAttribute(HazelcastNode.NODE_NAME);
    }

    @Override
    public Integer getNodePort() {
        return getAttribute(HazelcastNode.NODE_PORT);
    }

    @Override
    public String getHostname() { 
        return getAttribute(HOSTNAME); 
    }
    
    @Override
    public String getHostAddress() { 
        return getAttribute(ADDRESS); 
    }
    
    @Override
    public String getPrivateIpAddress() {
        return getAttribute(SUBNET_ADDRESS);
    }
    
    @Override
    public String getListenAddress() {
        String listenAddress = getPrivateIpAddress();
        
        if (Strings.isBlank(listenAddress)) {
            listenAddress = getAttribute(ADDRESS);
        }
        
        if (LOG.isInfoEnabled()) {
            LOG.info("Node {} is listening on {}", this, listenAddress);
        }

         
        return listenAddress;
    }


    @Override
    public String getHeapMemorySize() {
        return getConfig(HazelcastNode.NODE_HEAP_MEMORY_SIZE);
    }

}
