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
package brooklyn.entity.messaging.kafka;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka zookeeper instance.
 */
@ImplementedBy(KafkaZookeeperImpl.class)
public interface KafkaZookeeper extends SoftwareProcess, UsesJmx, Kafka {

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION = Kafka.SUGGESTED_VERSION;

    @SetFromFlag("zookeeperPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_PORT = new PortAttributeSensorAndConfigKey("zookeeper.port", "Zookeeper port", "2181+");

    /** Location of the configuration file template to be copied to the server.*/
    @SetFromFlag("zookeeperConfig")
    BasicConfigKey<String> ZOOKEEPER_CONFIG_TEMPLATE = new BasicConfigKey<String>(
            String.class, "kafka.config.zookeeper", "Zookeeper configuration template (in freemarker format)", "classpath://brooklyn/entity/messaging/kafka/zookeeper.properties");

    BasicAttributeSensor<Long> OUTSTANDING_REQUESTS = new BasicAttributeSensor<Long>(Long.class, "kafka.zookeeper.outstandingRequests", "Outstanding request count");
    BasicAttributeSensor<Long> PACKETS_RECEIVED = new BasicAttributeSensor<Long>(Long.class, "kafka.zookeeper.packets.received", "Total packets received");
    BasicAttributeSensor<Long> PACKETS_SENT = new BasicAttributeSensor<Long>(Long.class, "kafka.zookeeper.packets.sent", "Total packets sent");

    Integer getZookeeperPort();

    String getHostname();

}
