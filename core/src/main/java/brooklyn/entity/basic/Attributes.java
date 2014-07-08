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
package brooklyn.entity.basic;

import java.util.List;
import java.util.Map;

import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.net.UserAndHostAndPort;

import com.google.common.collect.ImmutableList;

/**
 * This interface should be used to access {@link Sensor} definitions.
 */
public interface Attributes {
    
    BasicNotificationSensor<Void> LOCATION_CHANGED = new BasicNotificationSensor<Void>(
            Void.class, "entity.locationChanged", "Indicates that an entity's location has been changed");

    // TODO these should switch to being TemplatedStringAttributeSensorAndConfigKey
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "download.url", "URL pattern for downloading the installer (will substitute things like ${version} automatically)");

    BasicAttributeSensorAndConfigKey<Map<String,String>> DOWNLOAD_ADDON_URLS = new BasicAttributeSensorAndConfigKey(
            Map.class, "download.addon.urls", "URL patterns for downloading named add-ons (will substitute things like ${version} automatically)");


    /*
     * Port number attributes.
     */

    AttributeSensor<List<Integer>> PORT_NUMBERS = new BasicAttributeSensor(
            List.class, "port.list", "List of port numbers");
    
    AttributeSensor<List<Sensor<Integer>>> PORT_SENSORS = new BasicAttributeSensor(
            List.class, "port.list.sensors", "List of port number attributes");

    PortAttributeSensorAndConfigKey HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "http.port", "HTTP port", ImmutableList.of(8080,"18080+"));
    
    PortAttributeSensorAndConfigKey HTTPS_PORT = new PortAttributeSensorAndConfigKey(
            "https.port", "HTTP port (with SSL/TLS)", ImmutableList.of(8443,"18443+"));
                    
    PortAttributeSensorAndConfigKey SSH_PORT = new PortAttributeSensorAndConfigKey("ssh.port", "SSH port", 22);
    PortAttributeSensorAndConfigKey SMTP_PORT = new PortAttributeSensorAndConfigKey("smtp.port", "SMTP port", 25);
    PortAttributeSensorAndConfigKey DNS_PORT = new PortAttributeSensorAndConfigKey("dns.port", "DNS port", 53);
    PortAttributeSensorAndConfigKey AMQP_PORT = new PortAttributeSensorAndConfigKey("amqp.port", "AMQP port", "5672+");

    /*
     * Location/connection attributes.
     */

    AttributeSensor<String> HOSTNAME = Sensors.newStringSensor( "host.name", "Host name");
    AttributeSensor<String> ADDRESS = Sensors.newStringSensor( "host.address", "Host IP address");
    AttributeSensor<UserAndHostAndPort> SSH_ADDRESS = Sensors.newSensor(
            UserAndHostAndPort.class, 
            "host.sshAddress", 
            "user@host:port for ssh'ing (or null if inappropriate)");
    AttributeSensor<String> SUBNET_HOSTNAME = Sensors.newStringSensor( "host.subnet.hostname", "Host name as known internally in " +
    		"the subnet where it is running (if different to host.name)");
    AttributeSensor<String> SUBNET_ADDRESS = Sensors.newStringSensor( "host.subnet.address", "Host address as known internally in " +
            "the subnet where it is running (if different to host.name)");

    AttributeSensor<String> HOST_AND_PORT = Sensors.newStringSensor( "hostandport", "host:port" );

    /*
     * Lifecycle attributes
     */
    AttributeSensor<Boolean> SERVICE_UP = Sensors.newBooleanSensor("service.isUp", 
            "Whether the service is active and availability (confirmed and monitored)");
    
    AttributeSensor<Lifecycle> SERVICE_STATE = Sensors.newSensor(Lifecycle.class,
            "service.state", "Expected lifecycle state of the service");

    /*
     * Other metadata (optional)
     */
    
    AttributeSensor<Integer> PID = Sensors.newIntegerSensor("pid", "Process ID for the previously launched instance");

    AttributeSensor<String> LOG_FILE_LOCATION = new BasicAttributeSensor<String>(
            String.class, "log.location", "Log file location");
}
