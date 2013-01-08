package brooklyn.entity.messaging.activemq;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.jms.JMSBroker;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBroker extends JMSBroker<ActiveMQQueue, ActiveMQTopic> implements UsesJmx {
	private static final Logger log = LoggerFactory.getLogger(ActiveMQBroker.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "5.7.0");

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

    private JmxFeed jmxFeed;

    public ActiveMQBroker() {
        this(MutableMap.of(), null);
    }
    public ActiveMQBroker(Map properties) {
        this(properties, null);
    }
    public ActiveMQBroker(Entity parent) {
        this(MutableMap.of(), parent);
    }
	public ActiveMQBroker(Map properties, Entity parent) {
		super(properties, parent);
	}

	public void setBrokerUrl() {
		setAttribute(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)));
	}
	
	public ActiveMQQueue createQueue(Map properties) {
		ActiveMQQueue result = new ActiveMQQueue(properties);
        Entities.manage(result);
        result.init();
        result.create();
        return result;
	}

	public ActiveMQTopic createTopic(Map properties) {
		ActiveMQTopic result = new ActiveMQTopic(properties);
        Entities.manage(result);
        result.init();
        result.create();
        return result;
	}

    @Override     
    protected void connectSensors() {
        setAttribute(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)));
        
        String brokerMbeanName = "org.apache.activemq:BrokerName=localhost,Type=Broker";
        
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                        .objectName(brokerMbeanName)
                        .attributeName("BrokerId")
                        .onSuccess(Functions.forPredicate(Predicates.notNull()))
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
        return super.toStringHelper().add("openWirePort", getAttribute(OPEN_WIRE_PORT));
    }

    @Override
    public Class getDriverInterface() {
        return ActiveMQDriver.class;
    }
}
