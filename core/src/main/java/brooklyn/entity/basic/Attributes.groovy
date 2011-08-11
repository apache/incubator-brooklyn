package brooklyn.entity.basic

import java.util.List

import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor

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
    ConfiguredAttributeSensor<Integer> JMX_USER = [ Integer, "jmx.user", "JMX username" ]
    ConfiguredAttributeSensor<String> JMX_PASSWORD = [ String, "jmx.password", "JMX password" ]

    /*
     * Port number attributes.
     */

    BasicAttributeSensor<List<Integer>> PORT_NUMBERS = [ List, "port.list", "List of port numbers" ]
    BasicAttributeSensor<List<Sensor<Integer>>> PORT_SENSORS = [ List, "port.list.sensors", "List of port number attributes" ]

    ConfiguredAttributeSensor<Integer> SSH_PORT = [ Integer, "ssh.port", "SSH port", 22 ]
    ConfiguredAttributeSensor<Integer> SMTP_PORT = [ Integer, "smtp.port", "SMTP port", 25 ]
    ConfiguredAttributeSensor<Integer> DNS_PORT = [ Integer, "dns.port", "DNS port", 53 ]
    ConfiguredAttributeSensor<Integer> HTTP_PORT = [ Integer, "http.port", "HTTP port", 80 ]
    ConfiguredAttributeSensor<Integer> HTTPS_PORT = [ Integer, "https.port", "HTTP port (with SSL/TLS)", 443 ]
    ConfiguredAttributeSensor<Integer> AMQP_PORT = [ Integer, "amqp.port", "AMQP port", 5672 ]

    /*
     * Other attributes.
     */

    BasicAttributeSensor<String> HOSTNAME = [ String, "host.name", "Host name" ]
    BasicAttributeSensor<String> ADDRESS = [ String, "host.address", "Host IP address" ]
}
