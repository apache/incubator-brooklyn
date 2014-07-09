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
package brooklyn.entity.messaging.kafka;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.zookeeper.ZooKeeperNode;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka broker instance.
 */
@ImplementedBy(KafkaBrokerImpl.class)
public interface KafkaBroker extends SoftwareProcess, MessageBroker, UsesJmx, Kafka {

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, Duration.FIVE_MINUTES);

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = Kafka.SUGGESTED_VERSION;

    @SetFromFlag("kafkaPort")
    PortAttributeSensorAndConfigKey KAFKA_PORT = new PortAttributeSensorAndConfigKey("kafka.port", "Kafka port", "9092+");

    /** Location of the configuration file template to be copied to the server.*/
    @SetFromFlag("kafkaServerConfig")
    ConfigKey<String> KAFKA_BROKER_CONFIG_TEMPLATE = new BasicConfigKey<String>(String.class,
            "kafka.broker.configTemplate", "Kafka broker configuration template (in freemarker format)",
            "classpath://brooklyn/entity/messaging/kafka/server.properties");

    @SetFromFlag("zookeeper")
    ConfigKey<ZooKeeperNode> ZOOKEEPER = new BasicConfigKey<ZooKeeperNode>(ZooKeeperNode.class, "kafka.broker.zookeeper", "Kafka zookeeper entity");

    public static final PortAttributeSensorAndConfigKey INTERNAL_JMX_PORT = new PortAttributeSensorAndConfigKey(
            "internal.jmx.direct.port", "JMX internal port (started by Kafka broker, if using UsesJmx.JMX_AGENT_MODE is not null)", PortRanges.fromString("9999+"));

    AttributeSensor<Integer> BROKER_ID = Sensors.newIntegerSensor("kafka.broker.id", "Kafka unique broker ID");

    AttributeSensor<Long> FETCH_REQUEST_COUNT = Sensors.newLongSensor("kafka.broker.fetch.total", "Fetch request count");
    AttributeSensor<Long> TOTAL_FETCH_TIME = Sensors.newLongSensor("kafka.broker.fetch.time.total", "Total fetch request processing time (millis)");
    AttributeSensor<Double> MAX_FETCH_TIME = Sensors.newDoubleSensor("kafka.broker.fetch.time.max", "Max fetch request processing time (millis)");

    AttributeSensor<Long> PRODUCE_REQUEST_COUNT = Sensors.newLongSensor("kafka.broker.produce.total", "Produce request count");
    AttributeSensor<Long> TOTAL_PRODUCE_TIME = Sensors.newLongSensor("kafka.broker.produce.time.total", "Total produce request processing time (millis)");
    AttributeSensor<Double> MAX_PRODUCE_TIME = Sensors.newDoubleSensor("kafka.broker.produce.time.max", "Max produce request processing time (millis)");

    AttributeSensor<Long> BYTES_RECEIVED = Sensors.newLongSensor("kafka.broker.bytes.received", "Total bytes received");
    AttributeSensor<Long> BYTES_SENT = Sensors.newLongSensor("kafka.broker.bytes.sent", "Total bytes sent");
    
    Integer getKafkaPort();

    Integer getBrokerId();

    ZooKeeperNode getZookeeper();

}
