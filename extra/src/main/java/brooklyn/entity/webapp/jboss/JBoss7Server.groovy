package brooklyn.entity.webapp.jboss

import groovy.lang.MetaClass
import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

class JBoss7Server extends JavaWebApp {

    public static final ConfiguredAttributeSensor<Integer> MANAGEMENT_PORT = 
            [ Integer, "http.managementPort", "Management port", 9990 ]

    public static final BasicAttributeSensor<Integer> MANAGEMENT_STATUS =
            [ Integer, "webapp.http.managementStatus", "HTTP response code for the management server" ]
    public static final BasicAttributeSensor<Integer> BYTES_RECEIVED =
            [ Integer, "webapp.reqs.bytes.received", "Total bytes received by the webserver" ]
    public static final BasicAttributeSensor<Integer> BYTES_SENT =
            [ Integer, "webapp.reqs.bytes.sent", "Total bytes sent by the webserver" ]
    
    public JBoss7Server(Map flags=[:], Entity owner=null) {
        super(flags, owner)
        
        setConfigIfValNonNull(MANAGEMENT_PORT.configKey, flags.managementPort)
        jmxEnabled = false
    }
    
    @Override
    protected void waitForHttpPort() {
        // TODO Auto-generated method stub
    }

    @Override
    protected void initSensors() {
        super.initSensors()
        
        def host = getAttribute(JMX_HOST)
        def port = getAttribute(MANAGEMENT_PORT)
        
        httpAdapter = new HttpSensorAdapter(this)
        attributePoller.addSensor(MANAGEMENT_STATUS, httpAdapter.newStatusValueProvider("http://$host:$port/management/"))
        attributePoller.addSensor(SERVICE_UP, { getAttribute(MANAGEMENT_STATUS) == 200 } as ValueProvider<Boolean>)
        
        String queryUrl = "http://$host:$port/management/subsystem/web/connector/http/read-resource?include-runtime"
        attributePoller.addSensor(REQUEST_COUNT, httpAdapter.newJsonLongProvider(queryUrl, "requestCount"))
        attributePoller.addSensor(ERROR_COUNT, httpAdapter.newJsonLongProvider(queryUrl, "errorCount"))
        attributePoller.addSensor(TOTAL_PROCESSING_TIME, httpAdapter.newJsonLongProvider(queryUrl, "processingTime"))
        attributePoller.addSensor(MAX_PROCESSING_TIME, httpAdapter.newJsonLongProvider(queryUrl, "maxTime"))
        attributePoller.addSensor(BYTES_RECEIVED, httpAdapter.newJsonLongProvider(queryUrl, "bytesReceived"))
        attributePoller.addSensor(BYTES_SENT, httpAdapter.newJsonLongProvider(queryUrl, "bytesSent"))
    }

    @Override
    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return JBoss7SshSetup.newInstance(this, machine)
    }

}
