package brooklyn.entity.messaging.kafka;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.messaging.Topic;

public class KafkaTopic extends AbstractEntity implements Topic {

    public KafkaTopic() {
    }

    // kafka:type=kafka.logs.${topicName}

    @Override
    public String getTopicName() {
        return null; // TODO
    }

    @Override
    public void create() {
        // TODO
    }

    @Override
    public void delete() {
        // TODO
    }

}
