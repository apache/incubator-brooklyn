package brooklyn.entity.messaging.qpid;

import brooklyn.entity.messaging.Topic;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(QpidTopicImpl.class)
public interface QpidTopic extends QpidDestination, Topic {
}
