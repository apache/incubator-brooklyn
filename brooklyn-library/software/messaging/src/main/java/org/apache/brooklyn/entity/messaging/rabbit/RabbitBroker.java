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

import com.google.common.annotations.Beta;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.messaging.MessageBroker;
import org.apache.brooklyn.entity.messaging.amqp.AmqpServer;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
@Catalog(name="RabbitMQ Broker", description="RabbitMQ is an open source message broker software (i.e. message-oriented middleware) that implements the Advanced Message Queuing Protocol (AMQP) standard", iconUrl="classpath:///RabbitMQLogo.png")
@ImplementedBy(RabbitBrokerImpl.class)
public interface RabbitBroker extends SoftwareProcess, MessageBroker, AmqpServer {

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "3.6.0");

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://www.rabbitmq.com/releases/rabbitmq-server/v${version}/rabbitmq-server-generic-unix-${version}.tar.xz");
    
    @SetFromFlag("erlangVersion")
    public static final BasicConfigKey<String> ERLANG_VERSION = new BasicConfigKey<String>(String.class, "erlang.version", "Erlang runtime version", "18.2");

    @SetFromFlag("erlangDebRepoUrl")
    public static final BasicConfigKey<String> ERLANG_DEB_REPO_URL = new BasicConfigKey<String>(String.class, "erlang.deb.repo.url", 
            "Deb file used to configure an external Erlang repository which provides up to date packages for Ubuntu/Debian", 
            "http://packages.erlang-solutions.com/erlang-solutions_1.0_all.deb");

    @SetFromFlag("rabbitmqConfigTemplateUrl")
    ConfigKey<String> CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "rabbitmq.templateUrl", "Template file (in freemarker format) for the rabbitmq.config config file",
            "classpath://org/apache/brooklyn/entity/messaging/rabbit/rabbitmq.config");

    @SetFromFlag("amqpPort")
    public static final PortAttributeSensorAndConfigKey AMQP_PORT = AmqpServer.AMQP_PORT;

    @SetFromFlag("virtualHost")
    public static final BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = AmqpServer.VIRTUAL_HOST_NAME;

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = new BasicAttributeSensorAndConfigKey<String>(
            AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_9_1);

    @SetFromFlag("managmentPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_PORT = new PortAttributeSensorAndConfigKey(
            "rabbitmq.management.port", "Port on which management interface will be available", "15672+");

    public static AttributeSensor<String> MANAGEMENT_URL = Sensors.newStringSensor(
            "rabbitmq.management.url", "Management URL is only available if management plugin flag is true");

    @SetFromFlag("enableManagementPlugin")
    public static final ConfigKey<Boolean> ENABLE_MANAGEMENT_PLUGIN = ConfigKeys.newBooleanConfigKey(
            "rabbitmq.management.plugin", "Management plugin will be enabled", false);

    RabbitQueue createQueue(Map properties);

    // TODO required by RabbitDestination due to close-coupling between that and RabbitBroker; how best to improve?
    @Beta
    Map<String, String> getShellEnvironment();
    
    @Beta
    String getRunDir();
}
