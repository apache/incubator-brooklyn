package brooklyn.entity.messaging.rabbit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.messaging.MessageBroker
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.amqp.AmqpExchange
import brooklyn.entity.messaging.amqp.AmqpServer
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.SshSensorAdapter
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag
import brooklyn.event.adapter.AbstractSensorAdapter
import brooklyn.event.adapter.FunctionSensorAdapter
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * An {@link brooklyn.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
public class RabbitBroker extends SoftwareProcessEntity implements MessageBroker, AmqpServer {
    private static final Logger log = LoggerFactory.getLogger(RabbitBroker.class)

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "2.8.2" ]

    @SetFromFlag("erlangVersion")
    public static final BasicConfigKey<String> ERLANG_VERSION = [ String, "erlang.version", "Erlang runtime version", "R15B" ]
    
    @SetFromFlag("amqpPort")
    public static final PortAttributeSensorAndConfigKey AMQP_PORT = AmqpServer.AMQP_PORT

    @SetFromFlag("virtualHost")
    public static final BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = AmqpServer.VIRTUAL_HOST_NAME

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = [ AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_9_1 ]

    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME) }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION) }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT) }

    public RabbitBroker(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void postStart() {
        super.postStart()

        waitForServiceUp()

        driver.configure()

        // TODO implement this using AMQP connection, no external mechanism available
		// queueNames.each { String name -> addQueue(name) }

        setBrokerUrl();
    }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@%s:%d/%s"
        setAttribute(BROKER_URL, String.format(urlFormat, getAttribute(HOSTNAME), getAttribute(AMQP_PORT), getAttribute(VIRTUAL_HOST_NAME)))
    }

    public RabbitQueue createQueue(Map properties) {
        return new RabbitQueue(properties, this)
    }

    public RabbitSshDriver newDriver(SshMachineLocation machine) {
        return new RabbitSshDriver(this, machine)
    }

    @Override
    protected void connectSensors() {
       super.connectSensors();

        //we need to copy it in a local variable, else the isRunningCallable is not
        //able to find the field.
       final RabbitSshDriver rabbitSshDriver = driver;

       Callable isRunningCallable = new Callable(){
           Object call() {
              return rabbitSshDriver.isRunning()
           }
       };

       FunctionSensorAdapter serviceUpAdapter = sensorRegistry.register(new FunctionSensorAdapter(
                    period:10*TimeUnit.SECONDS,
                    isRunningCallable));

       sensorRegistry.activateAdapters();

       serviceUpAdapter.poll(SERVICE_UP);
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
        exchange = properties.exchange ?: defaultExchangeName

        init()
        create()
    }

    public void init() {
        if (!virtualHost) virtualHost = getConfig(RabbitBroker.VIRTUAL_HOST_NAME)
        setAttribute(RabbitBroker.VIRTUAL_HOST_NAME, virtualHost)
        if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)
        sshAdapter = sensorRegistry.register(new SshSensorAdapter(owner.driver.machine, env:owner.driver.shellEnvironment));
    }

    public void create() {
        connectSensors()
        sensorRegistry.activateAdapters()
    }
    
    public void delete() {
        sensorRegistry.deactivateAdapters()
    }

    public void connectSensors() { }

    public String getExchangeName() { exchange }
    public String getDefaultExchangeName() { AmqpExchange.DIRECT }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + [ 'virtualHost', 'exchange' ]
    }
}

public class RabbitQueue extends RabbitDestination implements Queue {

    public RabbitQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public String getName() { getDisplayName() }

    @Override
    public void create() {
        setAttribute QUEUE_NAME, name
        super.create()
    }

    public void connectSensors() {
        def queueAdapter = sshAdapter.command("${owner.driver.runDir}/sbin/rabbitmqctl list_queues -p /${virtualHost}  | grep '${queueName}'")
        queueAdapter.poll(QUEUE_DEPTH_BYTES) {
            if (it == null || exitStatus != 0) return -1
            return 0 // TODO parse out queue depth from output
        }
        queueAdapter.poll(QUEUE_DEPTH_MESSAGES) {
            if (it == null || exitStatus != 0) return -1
            return 0 // TODO parse out queue depth from output
        }
    }

    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName() { name }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + [ 'name' ]
    }
}