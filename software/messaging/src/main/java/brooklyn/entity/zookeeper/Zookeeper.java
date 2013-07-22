/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.zookeeper;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Apache Zookeeper instance.
 * <p>
 * Currently {@code abstract} as there is no generic Zookeeper driver.
 */
@ImplementedBy(AbstractZookeeperImpl.class)
public interface Zookeeper extends SoftwareProcess, UsesJmx {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "3.3.3");

    @SetFromFlag("zookeeperPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_PORT = new PortAttributeSensorAndConfigKey("zookeeper.port", "Zookeeper port", "2181+");

    AttributeSensor<Long> OUTSTANDING_REQUESTS = new BasicAttributeSensor<Long>(Long.class, "zookeeper.outstandingRequests", "Outstanding request count");
    AttributeSensor<Long> PACKETS_RECEIVED = new BasicAttributeSensor<Long>(Long.class, "zookeeper.packets.received", "Total packets received");
    AttributeSensor<Long> PACKETS_SENT = new BasicAttributeSensor<Long>(Long.class, "zookeeper.packets.sent", "Total packets sent");

    Integer getZookeeperPort();

    String getHostname();

}
