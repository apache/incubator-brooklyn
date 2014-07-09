package brooklyn.entity.messaging;

import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

/**
 * An interface that describes a messaging topic.
 */
public interface Topic {
    BasicAttributeSensorAndConfigKey<String> TOPIC_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "topic.name", "Topic name");

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

    String getTopicName();

}