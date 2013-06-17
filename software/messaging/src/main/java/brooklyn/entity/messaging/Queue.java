package brooklyn.entity.messaging;

import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.IntegerAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

/**
 * An interface that describes a messaging queue.
 */
public interface Queue {
    BasicAttributeSensorAndConfigKey<String> QUEUE_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "queue.name", "Queue name");

    AttributeSensor<Integer> QUEUE_DEPTH_BYTES = new IntegerAttributeSensor("queue.depth.bytes", "Queue depth in bytes");
    AttributeSensor<Integer> QUEUE_DEPTH_MESSAGES = new IntegerAttributeSensor("queue.depth.messages", "Queue depth in messages");
    
    /**
     * Create the queue.
     *
     * TODO make this an effector
     */
    abstract void create();

    /**
     * Delete the queue.
     *
     * TODO make this an effector
     */
    abstract void delete();

    String getQueueName();

}
