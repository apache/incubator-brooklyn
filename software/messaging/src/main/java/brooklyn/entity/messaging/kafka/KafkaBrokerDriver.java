package brooklyn.entity.messaging.kafka;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface KafkaBrokerDriver extends JavaSoftwareProcessDriver {

    Integer getKafkaPort();

}
