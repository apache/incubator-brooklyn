package brooklyn.entity.messaging.rabbit;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.event.adapter.FunctionSensorAdapter;
import brooklyn.util.MutableMap;

import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
public class RabbitBrokerImpl extends SoftwareProcessEntity implements RabbitBroker {
    private static final Logger log = LoggerFactory.getLogger(RabbitBrokerImpl.class);

    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME); }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION); }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT); }

    public RabbitBrokerImpl() {
        super();
    }
    public RabbitBrokerImpl(Map properties) {
        this(properties, null);
    }
    public RabbitBrokerImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RabbitBrokerImpl(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public RabbitDriver getDriver() {
        return (RabbitDriver) super.getDriver();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return getDriver().getShellEnvironment();
    }
    
    @Override
    public String getRunDir() {
        return getDriver().getRunDir();
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
