package brooklyn.entity.messaging

import brooklyn.entity.Entity
import brooklyn.event.basic.BasicAttributeSensor

/**
 * Marker interface identifying message brokers.
 */
public interface MessageBroker extends Entity {
    BasicAttributeSensor<String> BROKER_URL = [ String, "broker.url", "Broker Connection URL" ]
}
