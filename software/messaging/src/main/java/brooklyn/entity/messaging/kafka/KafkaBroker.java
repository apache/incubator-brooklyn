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
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka broker instance.
 */
@ImplementedBy(KafkaBrokerImpl.class)
public interface KafkaBroker extends SoftwareProcess, MessageBroker, UsesJmx, Kafka {

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION = Kafka.SUGGESTED_VERSION;

    @SetFromFlag("kafkaPort")
    PortAttributeSensorAndConfigKey KAFKA_PORT = new PortAttributeSensorAndConfigKey("kafka.port", "Kafka port", "9092+");

    /** Location of the configuration file template to be copied to the server.*/
    @SetFromFlag("serverConfig")
    BasicConfigKey<String> SERVER_CONFIG_TEMPLATE = new BasicConfigKey<String>(
            String.class, "kafka.config.server", "Server configuration template (in freemarker format)", "classpath://brooklyn/entity/messaging/kafka/server.properties");

    @SetFromFlag("zookeeper")
    BasicConfigKey<KafkaZookeeper> ZOOKEEPER = new BasicConfigKey<KafkaZookeeper>(KafkaZookeeper.class, "Kafka zookeeper entity");

    AttributeSensor<Long> BROKER_ID = new BasicAttributeSensor<Long>(Long.class, "kafka.broker.id", "Kafka unique broker ID");

    BasicAttributeSensor<Long> FETCH_REQUEST_COUNT = new BasicAttributeSensor<Long>(Long.class, "kafka.broker.fetch.total", "Fetch request count");
    BasicAttributeSensor<Long> TOTAL_FETCH_TIME = new BasicAttributeSensor<Long>(Long.class, "kafka.broker.fetch.time.total", "Total fetch request processing time (millis)");
    BasicAttributeSensor<Double> MAX_FETCH_TIME = new BasicAttributeSensor<Double>(Double.class, "kafka.broker.fetch.time.max", "Max fetch request processing time (millis)");

    BasicAttributeSensor<Long> PRODUCE_REQUEST_COUNT = new BasicAttributeSensor<Long>(Long.class, "kafka.broker.produce.total", "Produce request count");
    BasicAttributeSensor<Long> TOTAL_PRODUCE_TIME = new BasicAttributeSensor<Long>(Long.class, "kafka.broker.produce.time.total", "Total produce request processing time (millis)");
    BasicAttributeSensor<Double> MAX_PRODUCE_TIME = new BasicAttributeSensor<Double>(Double.class, "kafka.broker.produce.time.max", "Max produce request processing time (millis)");

    BasicAttributeSensor<Long> BYTES_RECEIVED = new BasicAttributeSensor<Long>(Long.class, "kafka.broker.bytes.received", "Total bytes received");
    BasicAttributeSensor<Long> BYTES_SENT = new BasicAttributeSensor<Long>(Long.class, "kafka.broker.bytes.sent", "Total bytes sent");

    Integer getKafkaPort();

    Long getBrokerId();

    KafkaZookeeper getZookeeper();

}
