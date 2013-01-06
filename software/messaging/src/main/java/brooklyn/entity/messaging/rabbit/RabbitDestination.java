package brooklyn.entity.messaging.rabbit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.event.adapter.SensorRegistry;
import brooklyn.event.adapter.SshSensorAdapter;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public abstract class RabbitDestination extends AbstractEntity implements AmqpExchange {
    public static final Logger log = LoggerFactory.getLogger(RabbitDestination.class);
    
    @SetFromFlag
    String virtualHost;

    protected String exchange;
    protected SshMachineLocation machine;
    protected Map<String,String> shellEnvironment;

    public RabbitDestination() {
        this(MutableMap.of(), null);
    }
    public RabbitDestination(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RabbitDestination(Map flags) {
        this(flags, null);
    }
    public RabbitDestination(Map properties, Entity parent) {
        super(properties, parent);
        exchange = JavaGroovyEquivalents.elvis((String)properties.get("exchange"), getDefaultExchangeName());

        init();
    }

    public void init() {
        if (virtualHost == null) virtualHost = getConfig(RabbitBroker.VIRTUAL_HOST_NAME);
        setAttribute(RabbitBroker.VIRTUAL_HOST_NAME, virtualHost);
        
        machine = (SshMachineLocation) Iterables.find(getParent().getLocations(), Predicates.instanceOf(SshMachineLocation.class));
        shellEnvironment = getParent().getDriver().getShellEnvironment();
    }

    public RabbitBroker getParent() {
        return (RabbitBroker) super.getParent();
    }
    
    public void create() {
        connectSensors();
    }
    
    public void delete() {
        disconnectSensors();
    }

    protected void connectSensors() { }

    protected void disconnectSensors() { }

    public String getExchangeName() { 
        return exchange;
    }
    
    public String getDefaultExchangeName() {
        return AmqpExchange.DIRECT;
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("virtualHost", getParent().getVirtualHost()).add("exchange", getExchangeName());
    }
}
