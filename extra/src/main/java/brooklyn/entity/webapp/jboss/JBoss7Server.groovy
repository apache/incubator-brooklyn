package brooklyn.entity.webapp.jboss

import groovy.lang.MetaClass

import java.util.Collection

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.adapter.HttpUrlSensorAdapter
import brooklyn.event.adapter.legacy.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.flags.SetFromFlag

class JBoss7Server extends JavaWebApp {

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
		super.start(locations)
		connectSensors()
		attributePoller.activate()
	}
	
	protected void connectSensors() {
//		//TODO sensorRegistry
		def http = attributePoller.register(
			new HttpUrlSensorAdapter("http://$host:$port/management/subsystem/web/connector/http/read-resource?include-runtime", period: 100)
			//TODO
//			new HttpUrlSensorAdapter("http://$host:$port/management/subsystem/web/connector/http/read-resource", period: 100).
//				vars("include-runtime":null)
		)
		with(http) {
			poll(MANAGEMENT_STATUS, { statusCode })
			poll(BYTES_SENT, { json.bytesSent })
			subUrl("extra/url", period:400).poll(XXX, { json "xxx" })
		}
		
		//TODO start callback. events. jmx. missing.
		
//
//		def jmx = attributePoller.register(new OldJmxSensorAdapter(this, 60*1000)).setBlockDuringPostStart(true).setStartIfNoSensors(false)
//		with(attributePoller) {
//			addSensor(XXX, jmx, { attribute "jboss.web:type=GlobalRequestProcessor,name=http-*", "processingTime" } )
//			//addSensor(JMX_URL, jmx, { getUrl() } )
//		}
//		//or
//		with(jmx) {
//			//! with
//			
//			
//			//x not this
//			register(XXX, attribute("jboss.web:type=GlobalRequestProcessor,name=http-*", "processingTime"), { it /* optional post-processing */ })
//			register(XXX, { attribute "jboss.web:type=GlobalRequestProcessor,name=http-*", "processingTime" } )
//			//! yes to this:
//			register(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("processingTime"), { it /* optional post-processing */ })  
//			register(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("table"), { tabular "abc" })  
//			register(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("table"), { tabular "123" })  
//			register(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("table"), { tabular "xyz" })  
//			register(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").notification("name"), { it /* optional post-processing */ })  
//			poll(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").operation("processingTime", args), { it /* optional post-processing */ })
//			
//			//! and to this:
//			objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("processingTime").register(XXX)
//			with (objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("table")) {
//			   register(XXX, { xxx })
//			}
//			objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("processingTime").register(XXX)
//			
//			//event/notification sensors
//			onPostStart(JMX_URL, { getUrl() })
//			onJmxError(ERROR_CHANNEL, { "JMX had error: "+errorCode } )
//		}
//		
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
//		attributePoller.register(ManualSensorAdaptor).register(SOME_MANUAL_SENSOR)
//		
//
// 		checkAllSensorsRegistered()
//		
//		//above called during preStart()
//		
//		//below called in postStart.  or use subscriptions???
//		attributePoller.adapters.each { it.postStart() }
//		
//		//or
//		attributePoller.adapters.find({ it in OldJmxSensorAdapter })?.connect(block: true, publish: (getEntityClass().hasSensor(JMX_URL)))
//		
//		//and in the connection thread
	}
	
	protected void initJmxSensors() {
		// jmx not used here
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

        //we could tidy to use this syntax
        //connectHttpSensor(MANAGEMENT_STATUS, { statusCode } )
        //connectHttpSensor(BYTES_SENT, { json "bytesSent" } )
        
        //should confirm all sensors connected
    }

    //could use @SetupContributor, and register all such methods during load?
    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return JBoss7SshSetup.newInstance(this, machine)
    }
    
    //other things to look at:
    // Sensors
    // Policy
    // Children / Groups
    // JavaWebServer interface
    // registering location support ?
}
