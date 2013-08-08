package brooklyn.entity.messaging.rabbit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.proxying.EntitySpec;

import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
public class RabbitBrokerImpl extends SoftwareProcessImpl implements RabbitBroker {
    private static final Logger log = LoggerFactory.getLogger(RabbitBrokerImpl.class);

    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME); }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION); }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT); }

    public RabbitBrokerImpl() {
        super();
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
        RabbitQueue result = addChild(EntitySpec.create(RabbitQueue.class).configure(properties));
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

        connectServiceUpIsRunning();

        setBrokerUrl();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("amqpPort", getAmqpPort());
    }
}
