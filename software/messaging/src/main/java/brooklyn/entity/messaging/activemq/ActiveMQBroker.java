package brooklyn.entity.messaging.activemq;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;
/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
@ImplementedBy(ActiveMQBrokerImpl.class)
public interface ActiveMQBroker extends SoftwareProcess, MessageBroker, UsesJmx {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "5.7.0");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    public static final BasicConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "activemq.install.mirror.url", "URL of mirror",
        "http://www.mirrorservice.org/sites/ftp.apache.org/activemq/apache-activemq");

    @SetFromFlag("tgzUrl")
    public static final BasicConfigKey<String> TGZ_URL = new BasicConfigKey<String>(String.class, "activemq.install.tgzUrl", "URL of TGZ download file", null);

    @SetFromFlag("openWirePort")
	public static final PortAttributeSensorAndConfigKey OPEN_WIRE_PORT = new PortAttributeSensorAndConfigKey("openwire.port", "OpenWire port", "61616+");

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(Attributes.JMX_USER, "admin");
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(Attributes.JMX_PASSWORD, "admin");
}
