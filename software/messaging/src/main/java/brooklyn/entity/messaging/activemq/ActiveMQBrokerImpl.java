package brooklyn.entity.messaging.activemq;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.messaging.jms.JMSBroker;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.MutableMap;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBrokerImpl extends JMSBroker<ActiveMQQueue, ActiveMQTopic> implements ActiveMQBroker {
	private static final Logger log = LoggerFactory.getLogger(ActiveMQBrokerImpl.class);

    private volatile JmxFeed jmxFeed;

    public ActiveMQBrokerImpl() {
        super();
    }
    public ActiveMQBrokerImpl(Map properties) {
        this(properties, null);
    }
    public ActiveMQBrokerImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
	public ActiveMQBrokerImpl(Map properties, Entity parent) {
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
