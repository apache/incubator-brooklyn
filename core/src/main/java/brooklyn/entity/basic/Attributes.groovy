package brooklyn.entity.basic

import java.util.List

import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicNotificationSensor
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.basic.PortRanges

/**
 * This interface should be used to access {@link Sensor} definitions.
 */
public interface Attributes {
    
    BasicNotificationSensor<Void> LOCATION_CHANGED = 
            [Void.class, "entity.locationChanged", "Indicates that an entity's location has been changed"]


    /*
     * Application information sensors.
     * @see SoftwareProcessEntities.SUGGESTED_VERSION
     */
    BasicAttributeSensor<Integer> VERSION = [ String, "version", "Version information" ]

    /*
     * JMX attributes.
     */

    //1099 is standard, others are made up to provide something of a standard
    PortAttributeSensorAndConfigKey JMX_PORT = [ "jmx.port", "JMX port", PortRanges.fromString("9001, 32099-32000") ]
    PortAttributeSensorAndConfigKey RMI_PORT = [ "rmi.port", "RMI port", PortRanges.fromString("1099, 31099-31000") ]
    BasicAttributeSensorAndConfigKey<String> JMX_USER = [ String, "jmx.user", "JMX username" ]
    BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = [ String, "jmx.password", "JMX password" ]
    BasicAttributeSensorAndConfigKey<String> JMX_CONTEXT = [ String, "jmx.context", "JMX context path", "jmxrmi" ]
    BasicAttributeSensorAndConfigKey<String> JMX_SERVICE_URL = [ String, "jmx.serviceurl", "The URL for connecting to the MBean Server" ]
    
    /*
     * Port number attributes.
     */

    BasicAttributeSensor<List<Integer>> PORT_NUMBERS = [ List, "port.list", "List of port numbers" ]
    BasicAttributeSensor<List<Sensor<Integer>>> PORT_SENSORS = [ List, "port.list.sensors", "List of port number attributes" ]

    PortAttributeSensorAndConfigKey HTTP_PORT = [ "http.port", "HTTP port", [8080,"18000+"] ]
    PortAttributeSensorAndConfigKey HTTPS_PORT = [ "https.port", "HTTP port (with SSL/TLS)", [443,8443,"18443+"] ]
                    
    PortAttributeSensorAndConfigKey SSH_PORT = [ "ssh.port", "SSH port", 22 ]
    PortAttributeSensorAndConfigKey SMTP_PORT = [ "smtp.port", "SMTP port", 25 ]
    PortAttributeSensorAndConfigKey DNS_PORT = [ "dns.port", "DNS port", 53 ]
    PortAttributeSensorAndConfigKey AMQP_PORT = [ "amqp.port", "AMQP port", "5672+" ]

    /*
     * Location/connection attributes.
     */

    BasicAttributeSensor<String> HOSTNAME = [ String, "host.name", "Host name" ]
    BasicAttributeSensor<String> ADDRESS = [ String, "host.address", "Host IP address" ]
	
    /*
     * Lifecycle attributes
     */
    
    BasicAttributeSensor<Lifecycle> SERVICE_STATE = [ Lifecycle, "service.state", "Service lifecycle state" ]
    
	/** optional */
    BasicAttributeSensor<String> LOG_FILE_LOCATION = [ String, "log.location", "log file location" ]
}
