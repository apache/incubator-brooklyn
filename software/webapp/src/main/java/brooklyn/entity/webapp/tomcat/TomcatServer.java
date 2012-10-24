package brooklyn.entity.webapp.tomcat;

import static java.lang.String.format;
import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.adapter.ConfigSensorAdapter;
import brooklyn.event.adapter.JmxObjectNameAdapter;
import brooklyn.event.adapter.JmxSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatServer extends JavaWebAppSoftwareProcess implements JavaWebAppService, UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(TomcatServer.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "7.0.32");

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
    
    private JmxSensorAdapter jmx;

    public TomcatServer(Map flags){
        this(flags,null);
    }

    public TomcatServer(Entity owner){
        this(new LinkedHashMap(),owner);
    }

    public TomcatServer(Map flags, Entity owner) {
        super(flags, owner);
    }

    @Override
    public void connectSensors() {
        super.connectSensors();

        sensorRegistry.register(new ConfigSensorAdapter());

        Map<String, Object> flags = new LinkedHashMap<String, Object>();
        flags.put("period", new TimeDuration(0, 0, 0, 0, 500));
        
        if (getDriver().isJmxEnabled()) {
            jmx = sensorRegistry.register(new JmxSensorAdapter(flags));

            JmxObjectNameAdapter requestProcessorObjectNameAdapter = jmx.objectName("Catalina:type=GlobalRequestProcessor,name=\"http-*\"");
            requestProcessorObjectNameAdapter.attribute("errorCount").subscribe(ERROR_COUNT);
            requestProcessorObjectNameAdapter.attribute("requestCount").subscribe(REQUEST_COUNT);
            requestProcessorObjectNameAdapter.attribute("processingTime").subscribe(TOTAL_PROCESSING_TIME);

            JmxObjectNameAdapter connectorObjectNameAdapter = jmx.objectName(format("Catalina:type=Connector,port=%s", getAttribute(HTTP_PORT)));
            connectorObjectNameAdapter.attribute("stateName").subscribe(CONNECTOR_STATUS);
            Closure closure = new Closure(this) {
                @Override
                public Object call(Object... args) {
                    return "STARTED".equals(args[0]);
                }
            };
            connectorObjectNameAdapter.attribute("stateName").subscribe(SERVICE_UP, closure);

            // If MBean is unreachable, then mark as service-down
            requestProcessorObjectNameAdapter.reachable().poll(new Function<Boolean,Void>() {
                @Override public Void apply(Boolean input) {
                    if (input != null && Boolean.FALSE.equals(input)) {
                        setAttribute(SERVICE_UP, false);
                    }
                    return null;
                }});
        } else {
            // if not using JMX
            LOG.warn("Tomcat running without JMX monitoring; limited visibility of service available");
            // TODO we could at least check the http/s is up
        }
    }

    @Override
    protected void postActivation() {
        super.postActivation();

        // wait for MBeans to be available, rather than just the process to have started
        LOG.info("Waiting for {} up, via {}", this, jmx == null ? "" : jmx.getConnectionUrl());
        waitForServiceUp(new TimeDuration(0, 0, 5, 0, 0));
    }

    @Override
    public Class getDriverInterface() {
        return Tomcat7Driver.class;
    }
}

