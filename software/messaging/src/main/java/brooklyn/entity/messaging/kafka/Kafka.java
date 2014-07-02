package brooklyn.entity.messaging.kafka;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * Shared Kafka broker and zookeeper properties.
 */
public interface Kafka {

    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.7.2-incubating");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.DOWNLOAD_URL, "http://mirror.catn.com/pub/apache/incubator/kafka/kafka-${version}/kafka-${version}-src.tgz");

    // TODO: Upgrade to version 0.8.0, which will require refactoring of the sensors to reflect the changes to the JMX beans
//    @SetFromFlag("downloadUrl")
//    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
//            Attributes.DOWNLOAD_URL, "http://mirror.catn.com/pub/apache/kafka/${version}/kafka-${version}-src.tgz");

}
