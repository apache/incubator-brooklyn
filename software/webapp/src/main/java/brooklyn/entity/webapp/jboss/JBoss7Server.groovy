package brooklyn.entity.webapp.jboss

import brooklyn.util.MutableMap
import groovy.time.TimeDuration

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.adapter.HttpSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor ;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.flags.SetFromFlag;

public class JBoss7Server extends JavaWebAppSoftwareProcess implements JavaWebAppService {

	public static final Logger log = LoggerFactory.getLogger(JBoss7Server.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
        new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "7.1.1.Final");

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

    public JBoss7Server(Map flags){
        this(flags, null);
    }

    public JBoss7Server(Map flags, Entity owner) {
        super(flags, owner);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        String host = getAttribute(HOSTNAME);
        int port = getAttribute(MANAGEMENT_PORT) + getAttribute(PORT_INCREMENT);
        HttpSensorAdapter http = new HttpSensorAdapter(
                MutableMap.of("period",new TimeDuration(0,0,0,200)),
                "http://$host:$port/management/subsystem/web/connector/http/read-resource",
        );
        http = http.vars(MutableMap.of("include-runtime",true)) ;
        http = sensorRegistry.register(http) ;
        http.poll(MANAGEMENT_STATUS, { responseCode }) ;
        http.poll(SERVICE_UP, { responseCode==200 }) ;
        http.poll(REQUEST_COUNT) { json.requestCount } ;
        http.poll(ERROR_COUNT) { json.errorCount };
        http.poll(TOTAL_PROCESSING_TIME) { json.processingTime } ;
        http.poll(MAX_PROCESSING_TIME) { json.maxTime } ;
        http.poll(BYTES_RECEIVED) { json.bytesReceived };
        http.poll(BYTES_SENT, { json.bytesSent }) ;
    }

    public JBoss7SshDriver newDriver(SshMachineLocation machine) {
        return new JBoss7SshDriver(this, machine);
    }
}
