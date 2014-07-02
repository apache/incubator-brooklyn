package brooklyn.entity.messaging.kafka;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface KafkaZooKeeperDriver extends JavaSoftwareProcessDriver {

    Integer getZookeeperPort();

}
