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

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.zookeeper.Zookeeper;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka zookeeper instance.
 */
@ImplementedBy(KafkaZookeeperImpl.class)
public interface KafkaZookeeper extends Zookeeper, Kafka {

    @SetFromFlag("startTimeout")
    public static final ConfigKey<Integer> START_TIMEOUT = SoftwareProcess.START_TIMEOUT;

    /** The Kafka version, not the Zookeeper version. */
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = Kafka.SUGGESTED_VERSION;

    /** Location of the kafka configuration file template to be copied to the server. */
    @SetFromFlag("kafkaZookeeperConfig")
    ConfigKey<String> KAFKA_ZOOKEEPER_CONFIG_TEMPLATE = new BasicConfigKey<String>(String.class,
            "kafka.zookeeper.configTemplate", "Kafka zookeeper configuration template (in freemarker format)",
            "classpath://brooklyn/entity/messaging/kafka/zookeeper.properties");

}
