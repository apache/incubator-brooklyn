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

import static java.lang.String.format;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
public class RabbitBrokerImpl extends SoftwareProcessImpl implements RabbitBroker {
    private static final Logger log = LoggerFactory.getLogger(RabbitBrokerImpl.class);

    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME); }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION); }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT); }

    public RabbitBrokerImpl() {
        super();
    }

    @Override
    public RabbitDriver getDriver() {
        return (RabbitDriver) super.getDriver();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return getDriver().getShellEnvironment();
    }
    
    @Override
    public String getRunDir() {
        return getDriver().getRunDir();
    }
    
    @Override
    protected void postStart() {
        super.postStart();

        getDriver().configure();

        // TODO implement this using AMQP connection, no external mechanism available
        // queueNames.each { String name -> addQueue(name) }
    }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@%s:%d/%s";
        sensors().set(BROKER_URL, format(urlFormat, getAttribute(HOSTNAME), getAttribute(AMQP_PORT), getAttribute(VIRTUAL_HOST_NAME)));
    }

    public RabbitQueue createQueue(Map properties) {
        RabbitQueue result = addChild(EntitySpec.create(RabbitQueue.class).configure(properties));
        result.create();
        return result;
    }

    @Override
    public Class<? extends RabbitDriver> getDriverInterface() {
        return RabbitDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        connectServiceUpIsRunning();

        setBrokerUrl();

        if (getEnableManagementPlugin()) {
            sensors().set(MANAGEMENT_URL, format("http://%s:%s/", getAttribute(HOSTNAME), getAttribute(MANAGEMENT_PORT)));
        }
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    public boolean getEnableManagementPlugin() {
        return Boolean.TRUE.equals(getConfig(ENABLE_MANAGEMENT_PLUGIN));
    }

    public Integer getManagementPort() {
        return getAttribute(MANAGEMENT_PORT);
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("amqpPort", getAmqpPort());
    }
}
