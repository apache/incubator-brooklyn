package brooklyn.entity.webapp.jboss

import groovy.lang.MetaClass
import brooklyn.entity.Entity
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.flags.SetFromFlag;

class JBoss7Server extends JavaWebApp {
    
    @SetFromFlag("managementPort")
    public static final ConfiguredAttributeSensor<Integer> MANAGEMENT_PORT = 
            [ Integer, "http.managementPort", "Management port", 9990 ]

    public static final BasicAttributeSensor<Integer> MANAGEMENT_STATUS =
            [ Integer, "webapp.http.managementStatus", "HTTP response code for the management server" ]
    public static final BasicAttributeSensor<Long> BYTES_RECEIVED =
            [ Long, "webapp.reqs.bytes.received", "Total bytes received by the webserver" ]
    public static final BasicAttributeSensor<Long> BYTES_SENT =
            [ Long, "webapp.reqs.bytes.sent", "Total bytes sent by the webserver" ]
    
    public JBoss7Server(Map flags=[:], Entity owner=null) {
        super(flags, owner)
        jmxEnabled = false
    }

    @Override
    public void addHttpSensors() {
        def host = getAttribute(HOSTNAME)
        def port = getAttribute(MANAGEMENT_PORT)
        String queryUrl = "http://$host:$port/management/subsystem/web/connector/http/read-resource?include-runtime"

        attributePoller.addSensor(MANAGEMENT_STATUS, httpAdapter.newStatusValueProvider(queryUrl))
        attributePoller.addSensor(SERVICE_UP, { getAttribute(MANAGEMENT_STATUS) == 200 } as ValueProvider<Boolean>)

        attributePoller.addSensor(REQUEST_COUNT, httpAdapter.newJsonLongProvider(queryUrl, "requestCount"))
        attributePoller.addSensor(ERROR_COUNT, httpAdapter.newJsonLongProvider(queryUrl, "errorCount"))
        attributePoller.addSensor(TOTAL_PROCESSING_TIME, httpAdapter.newJsonLongProvider(queryUrl, "processingTime"))
        attributePoller.addSensor(MAX_PROCESSING_TIME, httpAdapter.newJsonLongProvider(queryUrl, "maxTime"))
        attributePoller.addSensor(BYTES_RECEIVED, httpAdapter.newJsonLongProvider(queryUrl, "bytesReceived"))
        attributePoller.addSensor(BYTES_SENT, httpAdapter.newJsonLongProvider(queryUrl, "bytesSent"))
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return JBoss7SshSetup.newInstance(this, machine)
    }
}
