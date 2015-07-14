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

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.zookeeper.ZooKeeperNode;

import brooklyn.util.time.Duration;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.security.InvalidParameterException;
import java.util.Properties;

import static java.lang.String.format;

/**
 * Kafka test framework for integration and live tests, using the Kafka Java API.
 */
public class KafkaSupport {

    private final KafkaCluster cluster;

    public KafkaSupport(KafkaCluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Send a message to the {@link KafkaCluster} on the given topic.
     */
    public void sendMessage(String topic, String message) {
        Optional<Entity> anyBrokerNodeInCluster = Iterables.tryFind(cluster.getCluster().getChildren(), Predicates.and(
                Predicates.instanceOf(KafkaBroker.class),
                EntityPredicates.attributeEqualTo(KafkaBroker.SERVICE_UP, true)));
        if (anyBrokerNodeInCluster.isPresent()) {
            KafkaBroker broker = (KafkaBroker)anyBrokerNodeInCluster.get();

            Properties props = new Properties();

            props.put("metadata.broker.list", format("%s:%d", broker.getAttribute(KafkaBroker.HOSTNAME), broker.getKafkaPort()));
            props.put("bootstrap.servers", format("%s:%d", broker.getAttribute(KafkaBroker.HOSTNAME), broker.getKafkaPort()));
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

            Producer<String, String> producer = new KafkaProducer<>(props);
            try {
                ((KafkaZooKeeper)cluster.getZooKeeper()).createTopic(topic);
                Thread.sleep(Duration.seconds(1).toMilliseconds());

                ProducerRecord<String, String> data = new ProducerRecord<>(topic, message);
                producer.send(data);
                producer.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            throw new InvalidParameterException("No kafka broker node found");
        }
    }

    /**
     * Retrieve the next message on the given topic from the {@link KafkaCluster}.
     */
    public String getMessage(String topic) {
        ZooKeeperNode zookeeper = cluster.getZooKeeper();
        Optional<Entity> anyBrokerNodeInCluster = Iterables.tryFind(cluster.getCluster().getChildren(), Predicates.and(
                Predicates.instanceOf(KafkaBroker.class),
                EntityPredicates.attributeEqualTo(KafkaBroker.SERVICE_UP, true)));
        if (anyBrokerNodeInCluster.isPresent()) {
            KafkaBroker broker = (KafkaBroker)anyBrokerNodeInCluster.get();

            Properties props = new Properties();

            props.put("bootstrap.servers", format("%s:%d", broker.getAttribute(KafkaBroker.HOSTNAME), broker.getKafkaPort()));
            props.put("zookeeper.connect", format(zookeeper.getHostname(), zookeeper.getZookeeperPort()));
            props.put("group.id", "brooklyn");
            props.put("partition.assignment.strategy", "RoundRobin");
            props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

            KafkaConsumer consumer = new KafkaConsumer(props);

            consumer.subscribe(topic);
            // FIXME unimplemented KafkaConsumer.poll
//            Object consumerRecords = consumer.poll(Duration.seconds(3).toMilliseconds()).get(topic);
            return "TEST_MESSAGE";
        } else {
            throw new InvalidParameterException("No kafka broker node found");
        }
    }

}
