package brooklyn.entity.messaging

import groovy.lang.MetaClass

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.JavaApp
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.JmxSensorAdapter

import com.google.common.base.Preconditions

public abstract class JMSBroker<Q extends JMSDestination & Queue, T extends JMSDestination & Topic> extends JavaApp {
    public static final AttributeSensor<String> BROKER_URL = [ String, "broker.url", "Broker Connection URL" ]

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
        queueNames.each { String name -> addQueue(name) }
        topicNames.each { String name -> addTopic(name) }
        setBrokerUrl();
    }

    public abstract void setBrokerUrl();

    @Override
    public void preStop() {
        queues.each { String name, JMSDestination queue -> queue.destroy() }
        topics.each { String name, JMSDestination topic -> topic.destroy() }
    }

    /** TODO make this an effector */
    public void addQueue(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        queues.put name, createQueue(properties)
    }

    public abstract Q createQueue(Map properties);

    /** TODO make this an effector */
    public void addTopic(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        topics.put name, createTopic(properties)
    }

    public abstract T createTopic(Map properties);
}

public abstract class JMSDestination extends AbstractEntity {
    transient JmxSensorAdapter jmxAdapter
    transient AttributePoller attributePoller

    public JMSDestination(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        Preconditions.checkNotNull name, "Name must be specified"

        init()

        jmxAdapter = ((JMSBroker) getOwner()).jmxAdapter
        attributePoller = new AttributePoller(this)

        create()
    }

    public abstract void init();

    public abstract void addJmxSensors()

    public abstract void removeJmxSensors()

    public abstract void create();

    public abstract void delete();

    @Override
    public void destroy() {
        attributePoller.close()
        super.destroy()
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['name']
    }
}