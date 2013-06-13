package brooklyn.entity.messaging.activemq;

import brooklyn.entity.messaging.Topic;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(ActiveMQTopicImpl.class)
public interface ActiveMQTopic extends ActiveMQDestination, Topic {
}
