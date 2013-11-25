package brooklyn.entity.messaging.storm;

import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaAppUtils;
import brooklyn.entity.java.JavaSoftwareProcessDriver;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;

import com.google.common.base.Functions;
import com.google.common.base.Predicates;

public class StormImpl extends SoftwareProcessImpl implements Storm {
    
    private static final Logger log = LoggerFactory.getLogger(StormImpl.class);
    private static final ObjectName stormBean = JmxHelper.createObjectName("backtype.storm.daemon.nimbus:type=*");

    private JmxHelper jmxHelper;
    private volatile JmxFeed jmxFeed;
    
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

        if (((JavaSoftwareProcessDriver)getDriver()).isJmxEnabled()) {
            jmxHelper = new JmxHelper(this);
            jmxFeed = JmxFeed.builder()
                    .entity(this)
                    .period(3000, TimeUnit.MILLISECONDS)
                    .helper(jmxHelper)
                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP_JMX)
                            .objectName(stormBean)
                            .attributeName("Initialized")
                            .onSuccess(Functions.forPredicate(Predicates.notNull()))
                            .onException(Functions.constant(false)))
                    .build();
            JavaAppUtils.connectMXBeanSensors(this);
         } else {
            // if not using JMX
            log.warn("Storm running without JMX monitoring; limited visibility of service available");
            connectServiceUpIsRunning();
        }
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
        if (jmxHelper.isConnected()) jmxHelper.disconnect();
    }

}
