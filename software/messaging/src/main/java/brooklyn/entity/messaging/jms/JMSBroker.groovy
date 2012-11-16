package brooklyn.entity.messaging.jms


import java.util.Collection
import java.util.Map

import org.slf4j.Logger;
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.messaging.MessageBroker
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic

import com.google.common.base.Preconditions

public abstract class JMSBroker<Q extends JMSDestination & Queue, T extends JMSDestination & Topic> extends SoftwareProcessEntity implements MessageBroker {
    private static final Logger log = LoggerFactory.getLogger(JMSBroker.class)
    
    Collection<String> queueNames;
    Collection<String> topicNames;
    Map<String, Q> queues = [:];
    Map<String, T> topics = [:];

    public JMSBroker(Map properties = [:], Entity owner = null) {
        super(properties, owner)
    }
    
    public JMSBroker configure(Map properties) {
        if (queueNames==null) queueNames = []
        if (properties.queue) queueNames.add properties.remove('queue')
        if (properties.queues) queueNames.addAll properties.remove('queues')

        if (topicNames==null) topicNames = []
        if (properties.topic) topicNames.add properties.remove('topic')
        if (properties.topics) topicNames.addAll properties.remove('topics')
        
        super.configure(properties)
    }

    @Override
    public void postStart() {
		super.postStart()
		
		//need to wait for JMX operations to be available
		waitForServiceUp()
		
        queueNames.each { String name -> addQueue(name) }
        topicNames.each { String name -> addTopic(name) }
        setBrokerUrl();
    }
	
    public abstract void setBrokerUrl();

    @Override
    public void preStop() {
        // If can't delete queues, continue trying to stop.
        // (e.g. in CI have seen activemq "BrokerStoppedException" thrown in queue.destroy()). 
        try {
            queues.each { String name, JMSDestination queue -> queue.destroy() }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error deleting queues from broker "+this+"; continuing with stop...", e);
        }
        
        try {
            topics.each { String name, JMSDestination topic -> topic.destroy() }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error deleting topics from broker "+this+"; continuing with stop...", e);
        }
        
        super.preStop();
    }
	
    /** TODO make this an effector */
    public void addQueue(String name, Map properties=[:]) {
		checkModifiable()
        properties.owner = this
        properties.name = name
        queues.put name, createQueue(properties)
    }

    public abstract Q createQueue(Map properties);

    /** TODO make this an effector */
    public void addTopic(String name, Map properties=[:]) {
		checkModifiable()
        properties.owner = this
        properties.name = name
        topics.put name, createTopic(properties)
    }

    public abstract T createTopic(Map properties);
}

public abstract class JMSDestination extends AbstractEntity {
    public JMSDestination(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        Preconditions.checkNotNull name, "Name must be specified"

        init()
    }

	public String getName() { getDisplayName() }
	
    public abstract void init();

    public abstract void connectSensors()

    public abstract void delete();

    public void destroy() {
        delete()
        super.destroy()
    }
}
