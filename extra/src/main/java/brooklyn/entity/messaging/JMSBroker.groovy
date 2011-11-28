package brooklyn.entity.messaging

import groovy.lang.MetaClass

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.legacy.JavaApp;
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.legacy.OldJmxSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor

import com.google.common.base.Preconditions

public abstract class JMSBroker<Q extends JMSDestination & Queue, T extends JMSDestination & Topic> extends JavaApp implements MessageBroker {
    Collection<String> queueNames = []
    Map<String, Q> queues = [:]
    Collection<String> topicNames = []
    Map<String, T> topics = [:]

    public JMSBroker(Map properties = [:], Entity owner = null) {
        super(properties, owner)

        if (properties.queue) queueNames.add properties.queue
        if (properties.queues) queueNames.addAll properties.queues

        if (properties.topic) topicNames.add properties.topic
        if (properties.topics) topicNames.addAll properties.topics
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

	public void waitForServiceUp() {}
	
    public abstract void setBrokerUrl();

    @Override
    public void preStop() {
    	super.preStop()
        queues.each { String name, JMSDestination queue -> queue.destroy() }
        topics.each { String name, JMSDestination topic -> topic.destroy() }
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
    transient OldJmxSensorAdapter jmxAdapter
    transient SensorRegistry sensorRegistry

    public JMSDestination(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        Preconditions.checkNotNull name, "Name must be specified"

        init()

        jmxAdapter = ((JMSBroker) getOwner()).jmxAdapter
        sensorRegistry = new SensorRegistry(this)

        create()
    }

	public String getName() { getDisplayName() }
	
    public abstract void init();

    public abstract void addJmxSensors()

    public abstract void removeJmxSensors()

    public abstract void create();

    public abstract void delete();

    @Override
    public void destroy() {
        sensorRegistry.close()
        super.destroy()
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['name']
    }
}