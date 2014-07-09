package brooklyn.entity.messaging.kafka;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.zookeeper.ZooKeeperNode;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka zookeeper instance.
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
            "classpath://brooklyn/entity/messaging/kafka/zookeeper.properties");

}
