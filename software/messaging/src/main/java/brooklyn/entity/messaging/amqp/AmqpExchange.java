package brooklyn.entity.messaging.amqp;

import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An interface that describes an AMQP exchange.
 */
public interface AmqpExchange {

    /* AMQP standard exchange names. */
    
    String DIRECT = "amq.direct";
    String TOPIC = "amq.topic";

    /** The AMQP exchange name {@link Sensor}. */
    @SetFromFlag("exchange")
    BasicAttributeSensorAndConfigKey<String> EXCHANGE_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "amqp.exchange.name", "AMQP exchange name");

    /**
     * Return the AMQP exchange name.
     */
    public String getExchangeName();
}
