package brooklyn.entity.webapp.tomcat;

import static java.lang.String.format;
import groovy.time.TimeDuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;


import com.google.common.base.Functions;
import com.google.common.base.Predicates;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatServerImpl extends JavaWebAppSoftwareProcessImpl implements TomcatServer {
    private static final Logger log = LoggerFactory.getLogger(TomcatServerImpl.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "7.0.34");

    /**
     * Tomcat insists on having a port you can connect to for the sole purpose of shutting it down.
     * Don't see an easy way to disable it; causes collisions in its out-of-the-box location of 8005,
     * so override default here to a high-numbered port.
     */
    @SetFromFlag("shutdownPort")
    public static final PortAttributeSensorAndConfigKey SHUTDOWN_PORT =
            new PortAttributeSensorAndConfigKey("tomcat.shutdownport", "Suggested shutdown port", PortRanges.fromString("31880+"));

    public static final BasicAttributeSensor<String> CONNECTOR_STATUS =
            new BasicAttributeSensor<String>(String.class, "webapp.tomcat.connectorStatus", "Catalina connector state name");

    public static final BasicAttributeSensor<String> JMX_SERVICE_URL = Attributes.JMX_SERVICE_URL;
    
    private JmxFeed jmxFeed;

    public TomcatServerImpl() {
        super();
    }

    public TomcatServerImpl(Map flags) {
        this(flags,null);
    }

    public TomcatServerImpl(Entity parent) {
        this(new LinkedHashMap(),parent);
    }

    public TomcatServerImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public void connectSensors() {
        super.connectSensors();

        ConfigToAttributes.apply(this);

        Map<String, Object> flags = new LinkedHashMap<String, Object>();
        flags.put("period", new TimeDuration(0, 0, 0, 0, 500));
        
        if (getDriver().isJmxEnabled()) {
            String requestProcessorMbeanName = "Catalina:type=GlobalRequestProcessor,name=\"http-*\"";
            String connectorMbeanName = format("Catalina:type=Connector,port=%s", getAttribute(HTTP_PORT));

            jmxFeed = JmxFeed.builder()
                    .entity(this)
                    .period(500, TimeUnit.MILLISECONDS)
                    .pollAttribute(new JmxAttributePollConfig<Integer>(ERROR_COUNT)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("errorCount"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(REQUEST_COUNT)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("requestCount"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(TOTAL_PROCESSING_TIME)
                            .objectName(requestProcessorMbeanName)
                            .attributeName("processingTime"))
                    .pollAttribute(new JmxAttributePollConfig<String>(CONNECTOR_STATUS)
                            .objectName(connectorMbeanName)
                            .attributeName("stateName"))
                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                            .objectName(connectorMbeanName)
                            .attributeName("stateName")
                            .onSuccess(Functions.forPredicate(Predicates.<Object>equalTo("STARTED")))
                            .onError(Functions.constant(false)))
                    .build();
            
        } else {
            // if not using JMX
            LOG.warn("Tomcat running without JMX monitoring; limited visibility of service available");
            // TODO we could at least check the http/s is up
        }
    }

    @Override
    public void waitForServiceUp() {
        // Increases wait-time by overriding this
        LOG.info("Waiting for {} up, via {}", this, jmxFeed == null ? "" : jmxFeed.getJmxUri());
        waitForServiceUp(new TimeDuration(0, 0, 5, 0, 0));
    }

    @Override
    public Class getDriverInterface() {
        return Tomcat7Driver.class;
    }
}

