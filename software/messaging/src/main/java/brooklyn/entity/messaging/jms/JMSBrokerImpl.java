package brooklyn.entity.messaging.jms;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.messaging.Queue;
import brooklyn.entity.messaging.Topic;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class JMSBrokerImpl<Q extends JMSDestination & Queue, T extends JMSDestination & Topic> extends SoftwareProcessImpl implements JMSBroker<Q,T> {
    private static final Logger log = LoggerFactory.getLogger(JMSBroker.class);
    
    Collection<String> queueNames;
    Collection<String> topicNames;
    Map<String, Q> queues = Maps.newLinkedHashMap();
    Map<String, T> topics = Maps.newLinkedHashMap();

    public JMSBrokerImpl() {
    }

    @Override
    public JMSBrokerImpl configure(Map properties) {
        if (queueNames==null) queueNames = Lists.newArrayList();
        if (groovyTruth(properties.get("queue"))) queueNames.add((String) properties.remove("queue"));
        if (groovyTruth(properties.get("queues"))) queueNames.addAll((Collection<String>) properties.remove("queues"));

        if (topicNames==null) topicNames = Lists.newArrayList();
        if (groovyTruth(properties.get("topic"))) topicNames.add((String) properties.remove("topic"));
        if (groovyTruth(properties.get("topics"))) topicNames.addAll((Collection<String>) properties.remove("topics"));
        
        return (JMSBrokerImpl) super.configure(properties);
    }

    @Override
    public Collection<String> getQueueNames() {
        return queueNames;
    }
    
    @Override
    public Collection<String> getTopicNames() {
        return topicNames;
    }

    @Override
    public Map<String, Q> getQueues() {
        return queues;
    }
    
    @Override
    public Map<String, T> getTopics() {
        return topics;
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setBrokerUrl();
    }

    // FIXME Need this to be "really" post-start, so called after sensor-polling is activated etc
    @Override
    protected void postStart() {
		super.postStart();
		
        for (String name : queueNames) {
            addQueue(name);
        }
        for (String name : topicNames) {
            addTopic(name);
        }
    }
	
    @Override
    public abstract void setBrokerUrl();

    @Override
    public void preStop() {
        // If can't delete queues, continue trying to stop.
        // (e.g. in CI have seen activemq "BrokerStoppedException" thrown in queue.destroy()). 
        try {
            for (JMSDestination queue : queues.values()) {
                queue.destroy();
            }
        } catch (Exception e) {
            log.warn("Error deleting queues from broker "+this+"; continuing with stop...", e);
        }
        
        try {
            for (JMSDestination topic : topics.values()) {
                topic.destroy();
            }
        } catch (Exception e) {
            log.warn("Error deleting topics from broker "+this+"; continuing with stop...", e);
        }
        
        super.preStop();
    }
	
    @Override
    public void addQueue(String name) {
        addQueue(name, MutableMap.of());
    }
    
    @Override
    public void addQueue(String name, Map properties) {
		checkModifiable();
        properties.put("name", name);
        queues.put(name, createQueue(properties));
    }

    @Override
    public abstract Q createQueue(Map properties);

    @Override
    public void addTopic(String name) {
        addTopic(name, MutableMap.of());
    }
    
    @Override
    public void addTopic(String name, Map properties) {
		checkModifiable();
        properties.put("name", name);
        topics.put(name, createTopic(properties));
    }

    @Override
    public abstract T createTopic(Map properties);
}
