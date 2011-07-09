package brooklyn.entity.basic

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey;

/**
 * This interface should be used to access {@link Sensor} definitions.
 */
public interface Attributes {
    /*
     * Application information sensors.
     */

    BasicAttributeSensor<Integer> VERSION = [ String, "version", "Version information" ]

    /*
     * JMX sensors.
     */

    BasicAttributeSensor<Integer> JMX_PORT = [ Integer, "jmx.port", "JMX port" ]
    BasicAttributeSensor<String> JMX_HOST = [ String, "jmx.host", "JMX host" ]
    BasicAttributeSensor<Integer> JMX_USER = [ Integer, "jmx.user", "JMX username" ]
    BasicAttributeSensor<String> JMX_PASSWORD = [ String, "jmx.password", "JMX password" ]

    /*
     * Port number sensors.
     */

    BasicAttributeSensor<Integer> HTTP_PORT = [ Integer, "http.port", "HTTP port" ]
    BasicAttributeSensor<Integer> AMQP_PORT = [ Integer, "amqp.port", "AMQP port" ]
}
