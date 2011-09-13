package brooklyn.entity.messaging

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor

/**
 * An interface that describes a single JMS Queue.
 */
public interface Queue {
    ConfiguredAttributeSensor<String> QUEUE_NAME = [ String, "jms.queue.name", "JMS queue name" ]

    BasicAttributeSensor<Integer> QUEUE_DEPTH_BYTES = [ Integer, "jms.queue.depth.bytes", "Queue depth in bytes" ]
    BasicAttributeSensor<Integer> QUEUE_DEPTH_MESSAGES = [ Integer, "jms.queue.depth.messages", "Queue depth in messages" ]
    
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
