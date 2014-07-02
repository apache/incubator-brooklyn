package brooklyn.entity.messaging.kafka;

import static org.testng.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Properties;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaMessageStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.javaapi.producer.Producer;
import kafka.javaapi.producer.ProducerData;
import kafka.message.Message;
import kafka.producer.ProducerConfig;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.zookeeper.ZooKeeperNode;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

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
        ZooKeeperNode zookeeper = cluster.getZooKeeper();
        Properties props = new Properties();
        props.put("zk.connect", String.format("%s:%d", zookeeper.getAttribute(Attributes.HOSTNAME), zookeeper.getZookeeperPort()));
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        ProducerConfig config = new ProducerConfig(props);

        Producer<String, String> producer = new Producer<String, String>(config);
        ProducerData<String, String> data = new ProducerData<String, String>(topic, message);
        producer.send(data);
        producer.close();
    }

    /**
     * Retrieve the next message on the given topic from the {@link KafkaCluster}.
     */
    public String getMessage(String topic) {
        ZooKeeperNode zookeeper = cluster.getZooKeeper();
        Properties props = new Properties();
        props.put("zk.connect", String.format("%s:%d", zookeeper.getAttribute(Attributes.HOSTNAME), zookeeper.getZookeeperPort()));
        props.put("zk.connectiontimeout.ms", "120000"); // two minutes
        props.put("groupid", "brooklyn");
        ConsumerConfig consumerConfig = new ConsumerConfig(props);

        ConsumerConnector consumer = Consumer.createJavaConsumerConnector(consumerConfig);
        List<KafkaMessageStream<Message>> streams = consumer.createMessageStreams(ImmutableMap.of(topic, 1)).get(topic);
        ConsumerIterator<Message> iterator = Iterables.getOnlyElement(streams).iterator();
        Message msg = iterator.next();

        assertTrue(msg.isValid());
        ByteBuffer buf = msg.payload();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        String payload = new String(data);
        return payload;
    }
}
