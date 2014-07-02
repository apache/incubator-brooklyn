package brooklyn.entity.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.zookeeper.AbstractZooKeeperImpl;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Kafka zookeeper instance.
 */
public class KafkaZooKeeperImpl extends AbstractZooKeeperImpl implements KafkaZooKeeper {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(KafkaZooKeeperImpl.class);

    public KafkaZooKeeperImpl() {
    }

    @Override
    public Class<?> getDriverInterface() {
        return KafkaZooKeeperDriver.class;
    }

}
