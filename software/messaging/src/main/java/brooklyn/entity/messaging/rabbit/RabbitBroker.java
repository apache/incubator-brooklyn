package brooklyn.entity.messaging.rabbit;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.event.adapter.FunctionSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
public class RabbitBroker extends SoftwareProcessEntity implements MessageBroker, AmqpServer {
    private static final Logger log = LoggerFactory.getLogger(RabbitBroker.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "2.8.7");

    @SetFromFlag("erlangVersion")
    public static final BasicConfigKey<String> ERLANG_VERSION = new BasicConfigKey<String>(String.class, "erlang.version", "Erlang runtime version", "R15B");
    
    @SetFromFlag("amqpPort")
    public static final PortAttributeSensorAndConfigKey AMQP_PORT = AmqpServer.AMQP_PORT;

    @SetFromFlag("virtualHost")
    public static final BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = AmqpServer.VIRTUAL_HOST_NAME;

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = new BasicAttributeSensorAndConfigKey<String>(
            AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_9_1);

    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME); }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION); }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT); }

    public RabbitBroker() {
        this(MutableMap.of(), null);
    }
    public RabbitBroker(Map properties) {
        this(properties, null);
    }
    public RabbitBroker(Entity owner) {
        this(MutableMap.of(), owner);
    }
    public RabbitBroker(Map properties, Entity owner) {
        super(properties, owner);
    }

    @Override
    public RabbitDriver getDriver() {
        return (RabbitDriver) super.getDriver();
    }

    @Override
    protected void postStart() {
        super.postStart();

        getDriver().configure();

        // TODO implement this using AMQP connection, no external mechanism available
		// queueNames.each { String name -> addQueue(name) }
    }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@%s:%d/%s";
        setAttribute(BROKER_URL, String.format(urlFormat, getAttribute(HOSTNAME), getAttribute(AMQP_PORT), getAttribute(VIRTUAL_HOST_NAME)));
    }

    public RabbitQueue createQueue(Map properties) {
        RabbitQueue result = new RabbitQueue(properties, this);
        Entities.manage(result);
        result.create();
        return result;
    }

    @Override
    public Class<? extends RabbitDriver> getDriverInterface() {
        return RabbitDriver.class;
    }

    @Override
    protected void connectSensors() {
       super.connectSensors();

       FunctionSensorAdapter serviceUpAdapter = sensorRegistry.register(new FunctionSensorAdapter(
                    MutableMap.of("period", 10*1000),
                    new Callable<Boolean>(){
                        public Boolean call() {
                           return getDriver().isRunning();
                        }
                    }));

       serviceUpAdapter.poll(SERVICE_UP);
       
       setBrokerUrl();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("amqpPort", getAmqpPort());
    }
}
