package brooklyn.entity.messaging.storm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class StormImpl extends SoftwareProcessImpl implements Storm {
    
    private static final Logger log = LoggerFactory.getLogger(StormImpl.class);

    public StormImpl() {}
    
    @Override
    public String getHostname() { return getAttribute(HOSTNAME); }

    @Override
    public Role getRole() { return getAttribute(ROLE); }

    @Override
    public String getStormConfigTemplateUrl() { return getConfig(STORM_CONFIG_TEMPLATE_URL); }   
    
    @Override
    public Class getDriverInterface() {
        return StormDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();

/*
        if (((JavaSoftwareProcessDriver)getDriver()).isJmxEnabled()) {
            jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Long>(OUTSTANDING_REQUESTS)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("OutstandingRequests")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_RECEIVED)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("PacketsReceived")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_SENT)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("PacketsSent")
                        .onError(Functions.constant(-1l)))
                .build();
        }
*/
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        //if (jmxFeed != null) jmxFeed.stop();
    }

}
