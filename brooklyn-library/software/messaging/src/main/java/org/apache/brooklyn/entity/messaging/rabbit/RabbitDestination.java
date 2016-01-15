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
package org.apache.brooklyn.entity.messaging.rabbit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.entity.messaging.amqp.AmqpExchange;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public abstract class RabbitDestination extends AbstractEntity implements AmqpExchange {
    public static final Logger log = LoggerFactory.getLogger(RabbitDestination.class);
    
    private String virtualHost;
    private String exchange;
    protected SshMachineLocation machine;
    protected Map<String,String> shellEnvironment;

    public RabbitDestination() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        
        exchange = (getConfig(EXCHANGE_NAME) != null) ? getConfig(EXCHANGE_NAME) : getDefaultExchangeName();
        virtualHost = getConfig(RabbitBroker.VIRTUAL_HOST_NAME);
        sensors().set(RabbitBroker.VIRTUAL_HOST_NAME, virtualHost);
        
        machine = (SshMachineLocation) Iterables.find(getParent().getLocations(), Predicates.instanceOf(SshMachineLocation.class));
        shellEnvironment = getParent().getShellEnvironment();
    }

    // FIXME Should return RabbitBroker; won't work if gets a proxy rather than "real" entity
    @Override
    public RabbitBroker getParent() {
        return (RabbitBroker) super.getParent();
    }
    
    public void create() {
        connectSensors();
    }
    
    public void delete() {
        disconnectSensors();
    }

    protected void connectSensors() { }

    protected void disconnectSensors() { }

    public String getVirtualHost() {
        return virtualHost;
    }
    
    @Override
    public String getExchangeName() { 
        return exchange;
    }
    
    public String getDefaultExchangeName() {
        return AmqpExchange.DIRECT;
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("virtualHost", getParent().getVirtualHost()).add("exchange", getExchangeName());
    }
}
