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

import static org.testng.Assert.*;

import java.util.List;
import java.util.Properties;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaMessageStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.javaapi.producer.Producer;
import kafka.javaapi.producer.ProducerData;
import kafka.message.Message;
import kafka.producer.ProducerConfig;
import brooklyn.entity.basic.Attributes;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class KafkaSupport {

    private final KafkaZookeeper zookeeper;

    public KafkaSupport(KafkaZookeeper zookeeper) {
        this.zookeeper = zookeeper;
    }

    public void sendMessage(String topic, String message) {
        Properties props = new Properties();
        props.put("zk.connect", String.format("%s:%d", zookeeper.getAttribute(Attributes.HOSTNAME), zookeeper.getZookeeperPort()));
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        ProducerConfig config = new ProducerConfig(props);
        Producer<String, String> producer = new Producer<String, String>(config);
        ProducerData<String, String> data = new ProducerData<String, String>(topic, message);
        producer.send(data);
        producer.close();
    }

    public List<String> getMessage(String topic) {
        Properties props = new Properties();
        props.put("zk.connect", String.format("%s:%d", zookeeper.getAttribute(Attributes.HOSTNAME), zookeeper.getZookeeperPort()));
        props.put("zk.connectiontimeout.ms", "1000000");
        props.put("groupid", "test_group");
        ConsumerConfig consumerConfig = new ConsumerConfig(props);
        ConsumerConnector consumer = Consumer.createJavaConsumerConnector(consumerConfig);
        List<KafkaMessageStream<Message>> streams = consumer.createMessageStreams(ImmutableMap.of(topic, 1)).get(topic);
        List<String> messages = Lists.newArrayList();
        for (Message msg : Iterables.getOnlyElement(streams)) {
            assertTrue(msg.isValid());
            String payload = new String(msg.payload().array());
            messages.add(payload);
          }
        return messages;
    }
}
