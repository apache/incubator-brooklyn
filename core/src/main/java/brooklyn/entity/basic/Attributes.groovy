package brooklyn.entity.basic

import java.util.List

import brooklyn.event.Sensor
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
     * JMX attributes.
     */

    BasicAttributeSensor<Integer> JMX_PORT = [ Integer, "jmx.port", "JMX port" ]
    BasicAttributeSensor<String> JMX_HOST = [ String, "jmx.host", "JMX host" ]
    BasicAttributeSensor<Integer> JMX_USER = [ Integer, "jmx.user", "JMX username" ]
    BasicAttributeSensor<String> JMX_PASSWORD = [ String, "jmx.password", "JMX password" ]

    /*
     * Port number attributes.
     */

    BasicAttributeSensor<List<Integer>> PORT_NUMBERS = [ List, "port.list", "List of port numbers" ]
    BasicAttributeSensor<List<Sensor<Integer>>> PORT_SENSORS = [ List, "port.list.sensors", "List of port number attributes" ]

    BasicAttributeSensor<Integer> SSH_PORT = [ Integer, "ssh.port", "SSH port" ]
    BasicAttributeSensor<Integer> SMTP_PORT = [ Integer, "smtp.port", "SMTP port" ]
    BasicAttributeSensor<Integer> DNS_PORT = [ Integer, "dns.port", "DNS port" ]
    BasicAttributeSensor<Integer> HTTP_PORT = [ Integer, "http.port", "HTTP port" ]
    BasicAttributeSensor<Integer> HTTPS_PORT = [ Integer, "https.port", "HTTP port (with SSL/TLS)" ]
    BasicAttributeSensor<Integer> AMQP_PORT = [ Integer, "amqp.port", "AMQP port" ]
}
