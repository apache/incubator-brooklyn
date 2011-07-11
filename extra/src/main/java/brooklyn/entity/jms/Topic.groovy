package brooklyn.entity.jms

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey

import com.google.common.base.Preconditions

/**
 * An interface that describes a single JMS topic.
 */
public interface Topic {
    BasicConfigKey<String> TOPIC_NAME = [ String, "jms.topic.name", "JMS topic name" ]

    BasicAttributeSensor<Integer> QUEUE_DEPTH = [ Integer, "jms.queue.depth", "Queue depth in bytes" ]
    BasicAttributeSensor<Integer> MESSAGE_COUNT = [ Integer, "jms.message.count", "Number of messages" ]

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