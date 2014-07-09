package brooklyn.entity.messaging.activemq;


public class ActiveMQTopicImpl extends ActiveMQDestinationImpl implements ActiveMQTopic {
    public ActiveMQTopicImpl() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        setAttribute(TOPIC_NAME, getName());
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
