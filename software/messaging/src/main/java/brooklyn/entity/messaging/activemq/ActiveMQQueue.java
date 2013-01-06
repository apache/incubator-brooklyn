package brooklyn.entity.messaging.activemq;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.messaging.Queue;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.MutableMap;

public class ActiveMQQueue extends ActiveMQDestination implements Queue {
    public static final Logger log = LoggerFactory.getLogger(ActiveMQQueue.class);

    public ActiveMQQueue() {
        this(MutableMap.of(), null);
    }
    public ActiveMQQueue(Map properties) {
        this(properties, null);
    }
    public ActiveMQQueue(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public ActiveMQQueue(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public void init() {
        setAttribute(QUEUE_NAME, getName());
        super.init();
    }

    public String getQueueName() {
        return getName();
    }
    
    public void create() {
        if (log.isDebugEnabled()) log.debug("{} adding queue {} to broker {}", new Object[] {this, getName(), jmxHelper.getAttribute(brokerMBeanName, "BrokerId")});
        
        jmxHelper.operation(brokerMBeanName, "addQueue", getName());
        
        connectSensors();
    }

    public void delete() {
        jmxHelper.operation(brokerMBeanName, "removeQueue", getName());
        disconnectSensors();
    }

    @Override
    protected void connectSensors() {
        String queue = String.format("org.apache.activemq:BrokerName=localhost,Type=Queue,Destination=%s", getName());
        
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .helper(jmxHelper)
                .pollAttribute(new JmxAttributePollConfig<Integer>(QUEUE_DEPTH_MESSAGES)
                        .objectName(queue)
                        .attributeName("QueueSize"))
                .build();
    }
}
