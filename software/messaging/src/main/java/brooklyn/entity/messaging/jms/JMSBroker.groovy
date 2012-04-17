package brooklyn.entity.messaging.jms

import groovy.lang.MetaClass

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.messaging.MessageBroker
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.event.adapter.SensorRegistry

import com.google.common.base.Preconditions

public abstract class JMSBroker<Q extends JMSDestination & Queue, T extends JMSDestination & Topic> extends SoftwareProcessEntity implements MessageBroker {
    Collection<String> queueNames;
    Collection<String> topicNames;
    Map<String, Q> queues = [:];
    Map<String, T> topics = [:];

    public JMSBroker(Map properties = [:], Entity owner = null) {
        super(properties, owner)
    }
    
    public Entity configure(Map properties) {
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
        queues.each { String name, JMSDestination queue -> queue.destroy() }
        topics.each { String name, JMSDestination topic -> topic.destroy() }
        super.preStop()
    }

	protected void checkBrokerCanBeModified() {
		def state = getAttribute(SERVICE_STATE);
		if (getAttribute(SERVICE_STATE)==Lifecycle.RUNNING) return;
		if (getAttribute(SERVICE_STATE)==Lifecycle.STARTING) return;
		// TODO this check may be redundant or even inappropriate
		throw new IllegalStateException("cannot configure broker "+this+" in state "+state)
	}
	
    /** TODO make this an effector */
    public void addQueue(String name, Map properties=[:]) {
		checkBrokerCanBeModified()
        properties.owner = this
        properties.name = name
        queues.put name, createQueue(properties)
    }

    public abstract Q createQueue(Map properties);

    /** TODO make this an effector */
    public void addTopic(String name, Map properties=[:]) {
		checkBrokerCanBeModified()
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

        create()
    }

	public String getName() { getDisplayName() }
	
    public abstract void init();

    public abstract void connectSensors()

    public abstract void create();

    public abstract void delete();

    public void destroy() {
        delete()
        super.destroy()
    }
    
    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['name']
    }
}
