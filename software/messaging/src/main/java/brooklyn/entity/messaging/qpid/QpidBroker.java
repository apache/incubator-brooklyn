package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.messaging.jms.JMSBroker;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Sets;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Qpid broker instance, using AMQP 0-10.
 */
public class QpidBroker extends JMSBroker<QpidQueue, QpidTopic> implements UsesJmx, AmqpServer {
    private static final Logger log = LoggerFactory.getLogger(QpidBroker.class);

    /* Qpid runtime file locations for convenience. */

    public static final String CONFIG_XML = "etc/config.xml";
    public static final String VIRTUALHOSTS_XML = "etc/virtualhosts.xml";
    public static final String PASSWD = "etc/passwd";

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "0.20");
    
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
    public static final BasicConfigKey<Map> RUNTIME_FILES = new BasicConfigKey(
            Map.class, "qpid.files.runtime", "Map of files to be copied, keyed by destination name relative to runDir");

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.JMX_USER, "admin");
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.JMX_PASSWORD, "admin");

    private transient JmxFeed jmxFeed;
    
    public QpidBroker() {
        this(MutableMap.of(), null);
    }
    public QpidBroker(Map properties) {
        this(properties, null);
    }
    public QpidBroker(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public QpidBroker(Map properties, Entity parent) {
        super(properties, parent);
    }

    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME); }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION); }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT); }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@/%s?brokerlist='tcp://%s:%d'";
        setAttribute(BROKER_URL, format(urlFormat, getAttribute(VIRTUAL_HOST_NAME), getAttribute(HOSTNAME), getAttribute(AMQP_PORT)));
    }

    public void waitForServiceUp(long duration, TimeUnit units) {
        super.waitForServiceUp(duration, units);

        // Also wait for the MBean to exist (as used when creating queue/topic)
        JmxHelper helper = null;
        try {
            String virtualHost = getConfig(QpidBroker.VIRTUAL_HOST_NAME);
            ObjectName virtualHostManager = new ObjectName(format("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"%s\"", virtualHost));
            helper = new JmxHelper(this);
            helper.connect();
            helper.assertMBeanExistsEventually(virtualHostManager, units.toMillis(duration));
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            if (helper != null) helper.disconnect();
        }
    }
    
    public QpidQueue createQueue(Map properties) {
        QpidQueue result = new QpidQueue(properties, this);
        Entities.manage(result);
        result.init();
        result.create();
        return result;
    }

    public QpidTopic createTopic(Map properties) {
        QpidTopic result = new QpidTopic(properties, this);
        Entities.manage(result);
        result.init();
        result.create();
        return result;
    }

    @Override
    public Class getDriverInterface() {
        return QpidDriver.class;
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = Sets.newLinkedHashSet(super.getRequiredOpenPorts());
        ports.add(getAttribute(AMQP_PORT));
        ports.add(getAttribute(HTTP_MANAGEMENT_PORT));
        log.debug("getRequiredOpenPorts detected expanded (qpid) ports {} for {}", ports, this);
        return ports;
    }

    @Override
    protected void connectSensors() {
        String serverInfoMBeanName = "org.apache.qpid:type=ServerInformation,name=ServerInformation";
        
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                        .objectName(serverInfoMBeanName)
                        .attributeName("ProductVersion")
                        .onSuccess(new Function<Object,Boolean>() {
                                private boolean hasWarnedOfVersionMismatch;
                                @Override public Boolean apply(Object input) {
                                    if (input == null) return false;
                                    if (!hasWarnedOfVersionMismatch && !getConfig(SUGGESTED_VERSION).equals(input)) {
                                        log.warn("Qpid version mismatch: ProductVersion is {}, requested version is {}", input, getConfig(SUGGESTED_VERSION));
                                        hasWarnedOfVersionMismatch = true;
                                    }
                                    return true;
                                }})
                        .onError(Functions.constant(false)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("amqpPort", getAmqpPort());
    }
}
