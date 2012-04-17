package brooklyn.entity.messaging

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey

/**
 * An interface that describes a messaging queue.
 */
public interface Queue {
    BasicAttributeSensorAndConfigKey<String> QUEUE_NAME = [ String, "queue.name", "Queue name" ]

    BasicAttributeSensor<Integer> QUEUE_DEPTH_BYTES = [ Integer, "queue.depth.bytes", "Queue depth in bytes" ]
    BasicAttributeSensor<Integer> QUEUE_DEPTH_MESSAGES = [ Integer, "queue.depth.messages", "Queue depth in messages" ]
    
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
