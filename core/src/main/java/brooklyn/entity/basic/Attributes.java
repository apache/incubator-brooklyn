package brooklyn.entity.basic;

import java.util.List;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;

import com.google.common.collect.ImmutableList;

/**
 * This interface should be used to access {@link Sensor} definitions.
 */
public interface Attributes {
    
    BasicNotificationSensor<Void> LOCATION_CHANGED = new BasicNotificationSensor<Void>(
            Void.class, "entity.locationChanged", "Indicates that an entity's location has been changed");


    /**
     * Application information sensors.
     * 
     * @deprecated since 0.5; see {@link ConfigKeys#SUGGESTED_VERSION}
     */
    @Deprecated
    AttributeSensor<String> VERSION = Sensors.newStringSensor( "version", "Version information");

    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "download.url", "URL pattern for downloading the installer (will substitute things like ${version} automatically)");

    BasicAttributeSensorAndConfigKey<Map<String,String>> DOWNLOAD_ADDON_URLS = new BasicAttributeSensorAndConfigKey(
            Map.class, "download.addon.urls", "URL patterns for downloading named add-ons (will substitute things like ${version} automatically)");

    /*
     * JMX attributes.
     */

    /** chosen by java itself by default; setting this will only have any effect if using an agent 
     * @deprecated since 0.6.0 use keys from UsesJmx */
    @Deprecated
    PortAttributeSensorAndConfigKey JMX_PORT = new PortAttributeSensorAndConfigKey(
            "jmx.direct.port", "JMX direct/private port (e.g. JMX RMI server port, but not RMI registry port)", PortRanges.fromString("31001+")) {
        protected Integer convertConfigToSensor(PortRange value, Entity entity) {
            // TODO when using JmxAgentModes.NONE we should *not* convert, but leave it null
            // (e.g. to prevent a warning in e.g. ActiveMQIntegrationTest)
            // however supporting that means moving these keys to UsesJmx (which would be a good thing in any case)
            return super.convertConfigToSensor(value, entity);
        }
    };
    
    /** well-known port used by Java itself to start the RMI registry where JMX private port can be discovered;
     * ignored if using JMXMP agent 
     * @deprecated since 0.6.0 use keys from UsesJmx */
    @Deprecated
    PortAttributeSensorAndConfigKey RMI_REGISTRY_PORT = new PortAttributeSensorAndConfigKey(
            "rmi.registry.port", "RMI registry port, used for discovering JMX (private) port", PortRanges.fromString("1099, 19099+"));
    /** @deprecated since 0.6.0 use RMI_REGISTRY_PORT */ @Deprecated
    PortAttributeSensorAndConfigKey RMI_SERVER_PORT = RMI_REGISTRY_PORT;

    /** Currently only used to connect; not used to set up JMX (so only applies where systems set this up themselves)
     * @deprecated since 0.6.0 use keys from UsesJmx */
    @Deprecated
    BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.user", "JMX username");
    
    /** Currently only used to connect; not used to set up JMX (so only applies where systems set this up themselves)
     * @deprecated since 0.6.0 use keys from UsesJmx */
    @Deprecated
    BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.password", "JMX password");
    
    /** @deprecated since 0.6.0 use keys from UsesJmx */
    @Deprecated
    BasicAttributeSensorAndConfigKey<String> JMX_CONTEXT = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.context", "JMX context path", "jmxrmi");
    
    /** @deprecated since 0.6.0 use keys from UsesJmx */
    @Deprecated
    BasicAttributeSensorAndConfigKey<String> JMX_SERVICE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.service.url", "The URL for connecting to the MBean Server");
    
    /*
     * Port number attributes.
     */

    AttributeSensor<List<Integer>> PORT_NUMBERS = new BasicAttributeSensor(
            List.class, "port.list", "List of port numbers");
    
    AttributeSensor<List<Sensor<Integer>>> PORT_SENSORS = new BasicAttributeSensor(
            List.class, "port.list.sensors", "List of port number attributes");

    PortAttributeSensorAndConfigKey HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "http.port", "HTTP port", ImmutableList.of(8080,"18080+"));
    
    PortAttributeSensorAndConfigKey HTTPS_PORT = new PortAttributeSensorAndConfigKey(
            "https.port", "HTTP port (with SSL/TLS)", ImmutableList.of(8443,"18443+"));
                    
    PortAttributeSensorAndConfigKey SSH_PORT = new PortAttributeSensorAndConfigKey("ssh.port", "SSH port", 22);
    PortAttributeSensorAndConfigKey SMTP_PORT = new PortAttributeSensorAndConfigKey("smtp.port", "SMTP port", 25);
    PortAttributeSensorAndConfigKey DNS_PORT = new PortAttributeSensorAndConfigKey("dns.port", "DNS port", 53);
    PortAttributeSensorAndConfigKey AMQP_PORT = new PortAttributeSensorAndConfigKey("amqp.port", "AMQP port", "5672+");

    /*
     * Location/connection attributes.
     */

    AttributeSensor<String> HOSTNAME = Sensors.newStringSensor( "host.name", "Host name");
    AttributeSensor<String> ADDRESS = Sensors.newStringSensor( "host.address", "Host IP address");
    AttributeSensor<String> SUBNET_HOSTNAME = Sensors.newStringSensor( "host.subnet.hostname", "Host name as known internally in " +
    		"the subnet where it is running (if different to host.name)");
	
    /*
     * Lifecycle attributes
     */
    AttributeSensor<Boolean> SERVICE_UP = Sensors.newBooleanSensor("service.isUp", 
            "Whether the service is active and availability (confirmed and monitored)");
    
    AttributeSensor<Lifecycle> SERVICE_STATE = Sensors.newSensor(Lifecycle.class,
            "service.state", "Expected lifecycle state of the service");

    /*
     * Other metadata (optional)
     */
    
    AttributeSensor<Integer> PID = Sensors.newIntegerSensor("pid", "Process ID for the previously launched instance");

    AttributeSensor<String> LOG_FILE_LOCATION = new BasicAttributeSensor<String>(
            String.class, "log.location", "Log file location");
}
