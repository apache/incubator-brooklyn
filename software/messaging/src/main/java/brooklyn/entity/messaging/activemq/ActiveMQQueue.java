package brooklyn.entity.messaging.activemq;

import brooklyn.entity.messaging.Queue;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(ActiveMQQueueImpl.class)
public interface ActiveMQQueue extends ActiveMQDestination, Queue {
}
