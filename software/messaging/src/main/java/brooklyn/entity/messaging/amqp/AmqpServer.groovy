package brooklyn.entity.messaging.amqp

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag

/**
 * Marker interface identifying AMQP servers.
 */
public interface AmqpServer extends Entity {
    
    /* AMQP protocol version strings. */

    String AMQP_0_8 = "0-8"
    String AMQP_0_9 = "0-9"
    String AMQP_0_9_1 = "0-9-1"
    String AMQP_0_10 = "0-10"
    String AMQP_1_0 = "1-0"

    PortAttributeSensorAndConfigKey AMQP_PORT = Attributes.AMQP_PORT

    BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = [ String, "amqp.virtualHost", "AMQP virtual host name", "localhost" ]

    BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = [ String, "amqp.version", "AMQP protocol version" ]

    String getVirtualHost()

    String getAmqpVersion()

    Integer getAmqpPort()

}
