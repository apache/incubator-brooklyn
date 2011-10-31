package brooklyn.entity.webapp.jboss

import groovy.lang.MetaClass

import java.util.Collection
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.flags.SetFromFlag

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with;

public class JBoss7Server extends JavaWebApp {

	public static final Logger log = LoggerFactory.getLogger(JBoss7Server.class)
	
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
    }
	
	public void start(Collection<Location> locations) {
		try {
			super.start(locations)
			connectSensors()
			sensorRegistry.activateAdapters()
		} catch (Throwable e) {
			log.warn "errors starting ${this} (rethrowing): $e", e
			throw e
		}
	}
	
	protected void connectSensors() {
		//TODO should only be applied at initial deployment
		def config = sensorRegistry.register(new ConfigSensorAdapter())

		def host = getAttribute(HOSTNAME)
		def port = getAttribute(MANAGEMENT_PORT)
		def http = sensorRegistry.register(
			new HttpSensorAdapter("http://$host:$port/management/subsystem/web/connector/http/read-resource", period: 200*TimeUnit.MILLISECONDS).
				vars("include-runtime":null) )
		with(http) {
			poll(MANAGEMENT_STATUS, { responseCode })
			poll(SERVICE_UP, { responseCode==200 })

			poll(REQUEST_COUNT) { json.requestCount }
			poll(ERROR_COUNT) { json.errorCount }
			poll(TOTAL_PROCESSING_TIME) { json.processingTime }
			poll(MAX_PROCESSING_TIME) { json.maxTime }
			poll(BYTES_RECEIVED) { json.bytesReceived }
			poll(BYTES_SENT, { json.bytesSent })
		}
		

// 		checkAllSensorsRegistered()
		
//		//what about sensors where it doesn't necessarily make sense to register them here, e.g.:
//		//  config -- easy to exclude (by type)
//		//  lifecycle, member added -- ??
//		//  enriched sensors -- could add the policies here
//		//??
//		//solutions
//		//- could explicitly exclude here (and exclude things that already have a value, so really
//		//     dev just has to list things which don't yet have a value (in post-start) but which will get one somehow
//		//     but not using any of the adapters.  or things which might not be used.  that seems unlikely.
//		//- or just don't try to check that everything is registered
//		sensorRegistry.register(ManualSensorAdaptor).register(SOME_MANUAL_SENSOR)

		// process thinking...
		
//		//above called during preStart()
//		
//		//below called in postStart.  or use subscriptions???
//		sensorRegistry.adapters.each { it.postStart() }
//		
//		//or
//		sensorRegistry.adapters.find({ it in OldJmxSensorAdapter })?.connect(block: true, publish: (getEntityClass().hasSensor(JMX_URL)))
//		
//		//and in the connection thread
	}
	
	protected void initJmxSensors() {
		// jmx not used here
	}
	
    @Override
    public void addHttpSensors() {
//        def host = getAttribute(HOSTNAME)
//        def port = getAttribute(MANAGEMENT_PORT)
//        String queryUrl = "http://$host:$port/management/subsystem/web/connector/http/read-resource?include-runtime"
//
//        sensorRegistry.addSensor(MANAGEMENT_STATUS, httpAdapter.newStatusValueProvider(queryUrl))
//        sensorRegistry.addSensor(SERVICE_UP, { getAttribute(MANAGEMENT_STATUS) == 200 } as ValueProvider<Boolean>)
//
//        sensorRegistry.addSensor(REQUEST_COUNT, httpAdapter.newJsonLongProvider(queryUrl, "requestCount"))
//        sensorRegistry.addSensor(ERROR_COUNT, httpAdapter.newJsonLongProvider(queryUrl, "errorCount"))
//        sensorRegistry.addSensor(TOTAL_PROCESSING_TIME, httpAdapter.newJsonLongProvider(queryUrl, "processingTime"))
//        sensorRegistry.addSensor(MAX_PROCESSING_TIME, httpAdapter.newJsonLongProvider(queryUrl, "maxTime"))
//        sensorRegistry.addSensor(BYTES_RECEIVED, httpAdapter.newJsonLongProvider(queryUrl, "bytesReceived"))
//        sensorRegistry.addSensor(BYTES_SENT, httpAdapter.newJsonLongProvider(queryUrl, "bytesSent"))

        //we could tidy to use this syntax
        //connectHttpSensor(MANAGEMENT_STATUS, { statusCode } )
        //connectHttpSensor(BYTES_SENT, { json "bytesSent" } )
        
        //should confirm all sensors connected
    }

    //could use @SetupContributor, and register all such methods during load?
    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return JBoss7SshSetup.newInstance(this, machine)
    }
    
}
