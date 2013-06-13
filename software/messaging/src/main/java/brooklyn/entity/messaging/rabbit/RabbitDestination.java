package brooklyn.entity.messaging.rabbit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public abstract class RabbitDestination extends AbstractEntity implements AmqpExchange {
    public static final Logger log = LoggerFactory.getLogger(RabbitDestination.class);
    
    private String virtualHost;
    private String exchange;
    protected SshMachineLocation machine;
    protected Map<String,String> shellEnvironment;

    public RabbitDestination() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        
        exchange = (getConfig(EXCHANGE_NAME) != null) ? getConfig(EXCHANGE_NAME) : getDefaultExchangeName();
        virtualHost = getConfig(RabbitBroker.VIRTUAL_HOST_NAME);
        setAttribute(RabbitBroker.VIRTUAL_HOST_NAME, virtualHost);
        
        machine = (SshMachineLocation) Iterables.find(getParent().getLocations(), Predicates.instanceOf(SshMachineLocation.class));
        shellEnvironment = getParent().getShellEnvironment();
    }

    // FIXME Should return RabbitBroker; won't work if gets a proxy rather than "real" entity
    @Override
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

    public String getVirtualHost() {
        return virtualHost;
    }
    
    @Override
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
