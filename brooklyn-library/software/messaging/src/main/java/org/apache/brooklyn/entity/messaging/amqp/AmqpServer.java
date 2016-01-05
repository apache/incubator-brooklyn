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
package org.apache.brooklyn.entity.messaging.amqp;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;

/**
 * Marker interface identifying AMQP servers.
 */
public interface AmqpServer extends Entity {
    
    /* AMQP protocol version strings. */

    String AMQP_0_8 = "0-8";
    String AMQP_0_9 = "0-9";
    String AMQP_0_9_1 = "0-9-1";
    String AMQP_0_10 = "0-10";
    String AMQP_1_0 = "1-0";

    PortAttributeSensorAndConfigKey AMQP_PORT = Attributes.AMQP_PORT;

    BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "amqp.virtualHost", "AMQP virtual host name", "localhost");

    BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "amqp.version", "AMQP protocol version");

    String getVirtualHost();

    String getAmqpVersion();

    Integer getAmqpPort();
}
