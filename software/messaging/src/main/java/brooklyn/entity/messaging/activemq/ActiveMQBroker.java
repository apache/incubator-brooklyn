package brooklyn.entity.messaging.activemq;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.jms.JMSBroker;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;
/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
@Catalog(name="ActiveMQ Broker", description="ActiveMQ is an open source message broker which fully implements the Java Message Service 1.1 (JMS)", iconUrl="classpath:///activemq-logo.png")
@ImplementedBy(ActiveMQBrokerImpl.class)
public interface ActiveMQBroker extends SoftwareProcess, MessageBroker, UsesJmx, JMSBroker<ActiveMQQueue, ActiveMQTopic> {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "5.7.0");

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.DOWNLOAD_URL, "${driver.mirrorUrl}/${version}/apache-activemq-${version}-bin.tar.gz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    public static final BasicConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "activemq.install.mirror.url", "URL of mirror",
        "http://www.mirrorservice.org/sites/ftp.apache.org/activemq/apache-activemq");

    @SetFromFlag("openWirePort")
	public static final PortAttributeSensorAndConfigKey OPEN_WIRE_PORT = new PortAttributeSensorAndConfigKey("openwire.port", "OpenWire port", "61616+");

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(Attributes.JMX_USER, "admin");
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(Attributes.JMX_PASSWORD, "admin");
    
    @SetFromFlag("templateConfigurationUrl")
    public static final BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "activemq.templateConfigurationUrl", "Template file (in freemarker format) for the conf/activemq.xml file", 
            "classpath://brooklyn/entity/messaging/activemq/activemq.xml");
}
