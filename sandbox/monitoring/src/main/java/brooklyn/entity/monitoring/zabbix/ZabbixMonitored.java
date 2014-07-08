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
package brooklyn.entity.monitoring.zabbix;

import brooklyn.config.ConfigKey;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface ZabbixMonitored {

    /** The entity representing the Zabbix server monitoring an entity. */
    @SetFromFlag("zabbixServer")
    ConfigKey<ZabbixServer> ZABBIX_SERVER = new BasicConfigKey<ZabbixServer>(ZabbixServer.class, "zabbix.server.entity", "Zabbix server for this entity");

    PortAttributeSensorAndConfigKey ZABBIX_AGENT_PORT = new PortAttributeSensorAndConfigKey("zabbix.agent.port", "The port the Zabbix agent is listening on", "10050+");

    AttributeSensor<String> ZABBIX_AGENT_HOSTID = new BasicAttributeSensor<String>(String.class, "zabbix.agent.hostid", "The hostId for a Zabbix monitored agent");

}