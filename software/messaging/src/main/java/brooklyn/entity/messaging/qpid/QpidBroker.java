package brooklyn.entity.messaging.qpid;

import java.util.Map;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.messaging.jms.JMSBroker;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Qpid broker instance, using AMQP 0-10.
 */
@Catalog(name="Qpid Broker", description="Apache Qpid is an open-source messaging system, implementing the Advanced Message Queuing Protocol (AMQP)", iconUrl="classpath:///qpid-logo.jpeg")
@ImplementedBy(QpidBrokerImpl.class)
public interface QpidBroker extends SoftwareProcess, MessageBroker, UsesJmx, AmqpServer, JMSBroker<QpidQueue, QpidTopic> {

    /* Qpid runtime file locations for convenience. */

    public static final String CONFIG_XML = "etc/config.xml";
    public static final String VIRTUALHOSTS_XML = "etc/virtualhosts.xml";
    public static final String PASSWD = "etc/passwd";

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.20");
    
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.DOWNLOAD_URL, "http://download.nextag.com/apache/qpid/${version}/qpid-java-broker-${version}.tar.gz");

    @SetFromFlag("amqpPort")
    public static final PortAttributeSensorAndConfigKey AMQP_PORT = AmqpServer.AMQP_PORT;

    @SetFromFlag("virtualHost")
    public static final BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = AmqpServer.VIRTUAL_HOST_NAME;

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = new BasicAttributeSensorAndConfigKey<String>(
            AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_10);
    
    @SetFromFlag("httpManagementPort")
    public static final PortAttributeSensorAndConfigKey HTTP_MANAGEMENT_PORT = new PortAttributeSensorAndConfigKey("qpid.http-management.port", "Qpid HTTP management plugin port");

    /** Files to be copied to the server, map of "subpath/file.name": "classpath://foo/file.txt" (or other url) */
    @SetFromFlag("runtimeFiles")
    public static final BasicConfigKey<Map<String, String>> RUNTIME_FILES = new BasicConfigKey(
            Map.class, "qpid.files.runtime", "Map of files to be copied, keyed by destination name relative to runDir");

    /** Templates to be filled in and then copied to the server. See {@link #RUNTIME_FILES}. */
    @SetFromFlag("runtimeTemplates")
    public static final BasicConfigKey<Map<String, String>> RUNTIME_TEMPLATES = new BasicConfigKey(
            Map.class, "qpid.templates.runtime", "Map of templates to be filled in and copied, keyed by destination name relative to runDir");

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.JMX_USER, "admin");
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.JMX_PASSWORD, "admin");
}
