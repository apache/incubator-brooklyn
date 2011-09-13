package brooklyn.entity.messaging

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor

/**
 * An interface that describes a single JMS topic.
 */
public interface Topic {
    ConfiguredAttributeSensor<String> TOPIC_NAME = [ String, "jms.topic.name", "JMS topic name" ]

    /**
     * Create the topic.
     * 
     * TODO make this an effector
     */
    public abstract void create();

    /**
     * Delete the topic.
     * 
     * TODO make this an effector
     */
    public abstract void delete();
}