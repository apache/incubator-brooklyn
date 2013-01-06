package brooklyn.entity.messaging.activemq;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.messaging.Topic;
import brooklyn.util.MutableMap;

public class ActiveMQTopic extends ActiveMQDestination implements Topic {
    public ActiveMQTopic() {
        this(MutableMap.of(), null);
    }
    public ActiveMQTopic(Map properties) {
        this(properties, null);
    }
    public ActiveMQTopic(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public ActiveMQTopic(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public void init() {
        setAttribute(TOPIC_NAME, getName());
        super.init();
    }

    @Override
    public void create() {
        jmxHelper.operation(brokerMBeanName, "addTopic", getName());
        connectSensors();
    }

    public void delete() {
        jmxHelper.operation(brokerMBeanName, "removeTopic", getName());
        disconnectSensors();
    }

    public void connectSensors() {
        //TODO add sensors for topics
    }

    public String getTopicName() {
        return getName();
    }
}
