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
package org.apache.brooklyn.entity.messaging.kafka;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.entity.annotation.Effector;
import org.apache.brooklyn.entity.annotation.EffectorParam;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.zookeeper.ZooKeeperNode;
import org.apache.brooklyn.sensor.core.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Kafka zookeeper instance.
 */
@ImplementedBy(KafkaZooKeeperImpl.class)
public interface KafkaZooKeeper extends ZooKeeperNode, Kafka {

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = SoftwareProcess.START_TIMEOUT;

    /** The Kafka version, not the Zookeeper version. */
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = Kafka.SUGGESTED_VERSION;
    
    /** The Kafka version, not the Zookeeper version. */
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = Kafka.DOWNLOAD_URL;

    /** Location of the kafka configuration file template to be copied to the server. */
    @SetFromFlag("kafkaZookeeperConfig")
    ConfigKey<String> KAFKA_ZOOKEEPER_CONFIG_TEMPLATE = new BasicConfigKey<String>(String.class,
            "kafka.zookeeper.configTemplate", "Kafka zookeeper configuration template (in freemarker format)",
            "classpath://org/apache/brooklyn/entity/messaging/kafka/zookeeper.properties");

    @Effector(description = "Create a topic with a single partition and only one replica")
    void createTopic(@EffectorParam(name = "topic") String topic);
}
