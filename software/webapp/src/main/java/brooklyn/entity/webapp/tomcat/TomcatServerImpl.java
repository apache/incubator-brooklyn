package brooklyn.entity.webapp.tomcat;

import static java.lang.String.format;
import groovy.time.TimeDuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;

import com.google.common.base.Functions;
import com.google.common.base.Predicates;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatServerImpl extends JavaWebAppSoftwareProcessImpl implements TomcatServer {

    private static final Logger log = LoggerFactory.getLogger(TomcatServerImpl.class);

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
    
    private volatile JmxFeed jmxFeed;

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

