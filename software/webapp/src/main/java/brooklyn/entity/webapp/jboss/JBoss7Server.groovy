package brooklyn.entity.webapp.jboss

import brooklyn.util.MutableMap
import groovy.time.TimeDuration

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.adapter.HttpSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor ;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.flags.SetFromFlag;

public class JBoss7Server extends JavaWebAppSoftwareProcess implements JavaWebAppService {

	public static final Logger log = LoggerFactory.getLogger(JBoss7Server.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "7.1.1.Final");
    // note: 7.1.2.Final fixes many bugs but is not available for download,
    // see https://community.jboss.org/thread/197780
    // 7.2.0.Final should be out during Q3 2012

    public static final BasicConfigKey<String> BIND_ADDRESS =
            new BasicConfigKey<String>(String.class, "jboss.bind.address", "Address of interface JBoss should listen on, "+ 
                "defaulting 0.0.0.0 (but could set e.g. to attributeWhenReady(HOSTNAME)", "0.0.0.0");

    @SetFromFlag("managementPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementPort", "Management port", "9990+");

    @SetFromFlag("managementNativePort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_NATIVE_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementNativePort", "Management native port", "10999+");

    @SetFromFlag("portIncrement")
    public static final BasicAttributeSensorAndConfigKey PORT_INCREMENT =
            new BasicAttributeSensorAndConfigKey(Integer.class, "webapp.jboss.portIncrement", "Port increment, for all ports in config file", 0);

    public static final BasicAttributeSensor<Integer> MANAGEMENT_STATUS =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.jboss.managementStatus", "HTTP response code for the management server");

    private HttpFeed httpFeed;
    
    public JBoss7Server(Map flags){
        this(flags, null);
    }

    public JBoss7Server(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class getDriverInterface() {
        return JBoss7Driver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        String host = getAttribute(HOSTNAME);
        int port = getAttribute(MANAGEMENT_PORT) + getAttribute(PORT_INCREMENT);
        
        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(200)
                .baseUri(String.format("http://%s:%s/management/subsystem/web/connector/http/read-resource", host, port))
                .baseUriVars(ImmutableMap.of("include-runtime","true"))
                .poll(new HttpPollConfig<Integer>(MANAGEMENT_STATUS)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onError(Functions.constant(false)))
                .poll(new HttpPollConfig<Integer>(REQUEST_COUNT)
                        .onSuccess(HttpValueFunctions.jsonContents("requestCount", Integer.class)))
                .poll(new HttpPollConfig<Integer>(ERROR_COUNT)
                        .onSuccess(HttpValueFunctions.jsonContents("errorCount", Integer.class)))
                .poll(new HttpPollConfig<Integer>(TOTAL_PROCESSING_TIME)
                        .onSuccess(HttpValueFunctions.jsonContents("processingTime", Integer.class)))
                .poll(new HttpPollConfig<Integer>(MAX_PROCESSING_TIME)
                        .onSuccess(HttpValueFunctions.jsonContents("maxTime", Integer.class)))
                .poll(new HttpPollConfig<Long>(BYTES_RECEIVED)
                        .onSuccess(HttpValueFunctions.jsonContents("bytesReceived", Long.class)))
                .poll(new HttpPollConfig<Long>(BYTES_SENT)
                        .onSuccess(HttpValueFunctions.jsonContents("bytesSent", Long.class)))
                .build();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        
        if (httpFeed != null) httpFeed.stop();
    }
}
