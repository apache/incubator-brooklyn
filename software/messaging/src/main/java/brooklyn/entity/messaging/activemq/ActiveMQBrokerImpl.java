package brooklyn.entity.messaging.activemq;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.jms.JMSBrokerImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBrokerImpl extends JMSBrokerImpl<ActiveMQQueue, ActiveMQTopic> implements ActiveMQBroker {
	private static final Logger log = LoggerFactory.getLogger(ActiveMQBrokerImpl.class);

    private volatile JmxFeed jmxFeed;

    public ActiveMQBrokerImpl() {
        super();
    }

    @Override
    public void init() {
        super.init();
        Entities.getRequiredUrlConfig(this, TEMPLATE_CONFIGURATION_URL);
    }
    
	public void setBrokerUrl() {
		setAttribute(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)));
	}
	
    public Integer getJmxPort() {
        return !isJmxEnabled() ? Integer.valueOf(-1) : getAttribute(UsesJmx.JMX_PORT);
    }
    
    public Integer getOpenWirePort() {
        return getAttribute(OPEN_WIRE_PORT);
    }
    
    public boolean isJmxEnabled() {
        return Boolean.TRUE.equals(getConfig(USE_JMX));
    }

    @Override
	public ActiveMQQueue createQueue(Map properties) {
		ActiveMQQueue result = addChild(EntitySpec.create(ActiveMQQueue.class).configure(properties));
        Entities.manage(result);
        result.create();
        return result;
	}

    @Override
	public ActiveMQTopic createTopic(Map properties) {
		ActiveMQTopic result = addChild(EntitySpec.create(ActiveMQTopic.class).configure(properties));
        Entities.manage(result);
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
                        .onFailureOrException(Functions.constant(false)))
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
