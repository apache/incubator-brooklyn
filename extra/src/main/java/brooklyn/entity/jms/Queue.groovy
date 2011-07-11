package brooklyn.entity.jms

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey

/**
 * An interface that describes a single JMS Queue.
 */
public interface Queue {
    BasicConfigKey<String> QUEUE_NAME = [ String, "jms.queue.name", "JMS queue name" ]

    BasicAttributeSensor<Integer> QUEUE_DEPTH = [ Integer, "jms.queue.depth", "Queue depth in bytes" ]
    BasicAttributeSensor<Integer> MESSAGE_COUNT = [ Integer, "jms.message.count", "Number of messages" ]

    /**
     * Create the queue.
     *
     * TODO make this an effector
     */
    public abstract void create();

    /**
     * Delete the queue.
     *
     * TODO make this an effector
     */
    public abstract void delete();
}
