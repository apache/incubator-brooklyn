/*
 * TODO license
 */
package brooklyn.entity.messaging.rabbit

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit


import org.slf4j.Logger;
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.Queue;
import brooklyn.entity.messaging.Topic;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.event.adapter.SensorRegistry;
import brooklyn.event.adapter.SshSensorAdapter
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.Repeater

/**
 * An {@link brooklyn.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
public class RabbitBroker extends SoftwareProcessEntity implements MessageBroker, AmqpServer {
    private static final Logger log = LoggerFactory.getLogger(RabbitBroker.class)

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "2.7.1" ]

    @SetFromFlag("erlangVersion")
    public static final BasicConfigKey<String> ERLANG_VERSION = [ String, "erlang.version", "Erlang runtime version", "R15B" ]

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = [ AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_9_1 ]

    public String getVirtualHost() { return getConfig(VIRTUAL_HOST_NAME) }
    public String getAmqpVersion() { return getConfig(AMQP_VERSION) }

    public RabbitBroker(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@/%s?brokerlist='tcp://%s:%d?tcp_nodelay='true''&maxprefetch='1'"
        setAttribute(BROKER_URL, String.format(urlFormat, getAttribute(VIRTUAL_HOST_NAME), getAttribute(HOSTNAME), getAttribute(AMQP_PORT)))
    }

    public RabbitQueue createQueue(Map properties) {
        return new RabbitQueue(properties, this)
    }

    public RabbitTopic createTopic(Map properties) {
        return new RabbitTopic(properties, this)
    }

    public RabbitSshDriver newDriver(SshMachineLocation machine) {
        return new RabbitSshDriver(this, machine)
    }

    transient SshSensorAdapter sshAdapter;

    @Override     
    protected void connectSensors() {
        sshAdapter = sensorRegistry.register(new SshSensorAdapter(driver.machine));
        sshAdapter.command(CommonCommands.sudo("rabbitmqctl -q status"))
                .poll(SERVICE_UP) {
		            if (it == null || exitStatus != 0) return false
		            return (it =~ "running_applications.*RabbitMQ")
		        }
        sshAdapter.activateAdapter()
    }
    
    public void waitForServiceUp() {
        if (!Repeater.create(timeout: 60*TimeUnit.SECONDS)
                .rethrowException().repeat().every(1*TimeUnit.SECONDS).until { getAttribute(SERVICE_UP) }.
                run()) {
            throw new IllegalStateException("Could not connect via JMX to determine ${this} is up");
        }
        log.info("started JMS $this")
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + [ 'amqpPort' ]
    }

}

public abstract class RabbitDestination extends AbstractEntity implements AmqpExchange {
    public static final Logger log = LoggerFactory.getLogger(RabbitDestination.class);
    
    @SetFromFlag
    String virtualHost

    protected String exchange

    protected transient SensorRegistry sensorRegistry
    protected transient SshSensorAdapter sshAdapter

    public RabbitDestination(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
        if (!virtualHost) virtualHost = getConfig(RabbitBroker.VIRTUAL_HOST_NAME)
        setAttribute(RabbitBroker.VIRTUAL_HOST_NAME, virtualHost)
        if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)
        sshAdapter = sensorRegistry.register(new SshSensorAdapter(owner.driver.machine));
        sshAdapter.command("rabbitctl").poll(SERVICE_UP) {
            return (it != null)
        }
    }

    public void create() {
        sensorRegistry.activateAdapters()
    }
    
    public void delete() {
        sensorRegistry.deactivateAdapters()
    }

    /**
     * Return the AMQP exchange name.
     */
    public String getExchangeName() { return exchange }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['exchange']
    }
}

public class RabbitQueue extends RabbitDestination implements Queue {
    protected String name

    public RabbitQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void init() {
        setAttribute QUEUE_NAME, name
        super.init()
    }

    public void connectSensors() {
        sshAdapter.command(CommonCommands.sudo("rabbitmqctl list_queues -p /${virtualHost}  | grep '${queue}'")).poll(QUEUE_DEPTH_BYTES) {
            
        }
    }

    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName() { return String.format("'%s'/'%s'", exchangeName, name) }
}

public class RabbitTopic extends RabbitDestination implements Topic {
    protected String name

    public RabbitTopic(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    // TODO sensors
    public void connectSensors() { }
    
    @Override
    public void init() {
        setAttribute TOPIC_NAME, name
        super.init()
    }
}
