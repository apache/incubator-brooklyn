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
package brooklyn.entity.messaging.rabbit;

import java.util.Map;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
@Catalog(name="RabbitMQ Broker", description="RabbitMQ is an open source message broker software (i.e. message-oriented middleware) that implements the Advanced Message Queuing Protocol (AMQP) standard", iconUrl="classpath:///RabbitMQLogo.png")
@ImplementedBy(RabbitBrokerImpl.class)
public interface RabbitBroker extends SoftwareProcess, MessageBroker, AmqpServer {

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.8.7");

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://www.rabbitmq.com/releases/rabbitmq-server/v${version}/rabbitmq-server-generic-unix-${version}.tar.gz");
    
    @SetFromFlag("erlangVersion")
    public static final BasicConfigKey<String> ERLANG_VERSION = new BasicConfigKey<String>(String.class, "erlang.version", "Erlang runtime version", "R15B");
    
    @SetFromFlag("amqpPort")
    public static final PortAttributeSensorAndConfigKey AMQP_PORT = AmqpServer.AMQP_PORT;

    @SetFromFlag("virtualHost")
    public static final BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = AmqpServer.VIRTUAL_HOST_NAME;

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = new BasicAttributeSensorAndConfigKey<String>(
            AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_9_1);
    
    RabbitQueue createQueue(Map properties);

    // TODO required by RabbitDestination due to close-coupling between that and RabbitBroker; how best to improve?
    @Beta
    Map<String, String> getShellEnvironment();
    
    @Beta
    String getRunDir();
}
