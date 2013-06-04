package brooklyn.entity.messaging.jms;

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.Queue;
import brooklyn.entity.messaging.Topic;

import com.google.common.annotations.VisibleForTesting;

public interface JMSBroker<Q extends JMSDestination & Queue, T extends JMSDestination & Topic> extends SoftwareProcess, MessageBroker {
    
    @VisibleForTesting
    public Collection<String> getQueueNames();
    
    @VisibleForTesting
    public Collection<String> getTopicNames();

    @VisibleForTesting
    public Map<String, Q> getQueues();
    
    @VisibleForTesting
    public Map<String, T> getTopics();
    
    /** TODO make this an effector */
    public void addQueue(String name);
    
    public void addQueue(String name, Map properties);

    public Q createQueue(Map properties);

    /** TODO make this an effector */
    public void addTopic(String name);
    
    public void addTopic(String name, Map properties);

    public T createTopic(Map properties);
}
