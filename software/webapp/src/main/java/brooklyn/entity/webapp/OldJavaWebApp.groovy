package brooklyn.entity.webapp

import static java.util.concurrent.TimeUnit.SECONDS

import java.util.Collection;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.RollingTimeWindowMeanEnricher
import brooklyn.enricher.TimeWeightedDeltaEnricher
import brooklyn.entity.Entity
import brooklyn.entity.basic.legacy.JavaApp
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.legacy.OldHttpSensorAdapter
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.location.Location;
import brooklyn.util.internal.Repeater

/**
* An {@link Entity} representing a single java web application server instance.
* 
 * @deprecated will be deleted in 0.5. Use JavaWebAppSoftwareProcess
 */
@Deprecated
public abstract class OldJavaWebApp extends JavaApp implements JavaWebAppService {
	
    public static final Logger log = LoggerFactory.getLogger(OldJavaWebApp.class)

    transient OldHttpSensorAdapter httpAdapter

    Map environment = [:]
    
    // Set to false to prevent HTTP_SERVER and HTTP_STATUS being updated (useful for integration tests)
    boolean pollForHttpStatus = true

    public OldJavaWebApp(Map flags=[:], Entity owner=null) {
        super(flags, owner)
    }
    
    protected void waitForHttpPort() {
        boolean status = new Repeater("Wait for valid HTTP status (200 or 404)")
            .repeat()
            .every(1,SECONDS)
            .until {
                Integer response = getAttribute(HTTP_STATUS)
                return (response == 200 || response == 404)
             }
            .limitIterationsTo(120)
            .run()

        if (!status) {
            throw new EntityStartException("HTTP service on port "+getAttribute(HTTP_PORT)+" failed")
        }
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
		def http = getConfig(HTTP_PORT);
        super.getRequiredOpenPorts() + (http?[http.iterator().next()]:[])
    }

	protected void connectSensors() {
		super.connectSensors()
		
		// TODO Want to wire this up so doesn't go through SubscriptionManager;
		// but that's an optimisation we'll do later.
		addEnricher(TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(this, REQUEST_COUNT, REQUESTS_PER_SECOND))
		addEnricher(new RollingTimeWindowMeanEnricher<Double>(this, REQUESTS_PER_SECOND, AVG_REQUESTS_PER_SECOND,
			AVG_REQUESTS_PER_SECOND_PERIOD))

		initHttpSensors()
	}

    /**
      * @deprecated will be deleted in 0.5. use new mechanism
      */
	@Deprecated
	public void initHttpSensors() {
		httpAdapter = new OldHttpSensorAdapter(this)
		if (pollForHttpStatus) {
			def host = getAttribute(HOSTNAME)
			def port = getAttribute(HTTP_PORT)
			sensorRegistry.addSensor(HTTP_STATUS, httpAdapter.newStatusValueProvider("http://${host}:${port}/"))
			sensorRegistry.addSensor(HTTP_SERVER, httpAdapter.newHeaderValueProvider("http://${host}:${port}/", "Server"))
			log.info "{} waiting for http://${host}:${port}/", this
			waitForHttpPort()
			log.info "{} got http://${host}:${port}/", this
		}
	}

	
    @Override
    public void start(Collection<? extends Location> locations) {
		super.start(locations)
		
		log.info "started {}: httpPort {}, host {} and jmxPort {}", this,
				getAttribute(HTTP_PORT), getAttribute(HOSTNAME), getAttribute(JMX_PORT)

		def rootWar = getConfig(ROOT_WAR);
		if (rootWar) deploy(rootWar)

		def namedWars = getConfig(NAMED_WARS, []);
		namedWars.each { String it ->
			String name = it.substring(it.lastIndexOf('/')+1);
			deploy(new File(it), new File(driver.deployDir +"/"+name))
		}

//        def warFile = getConfig(WAR)
//        if (warFile) {
//            log.debug "Deploying {} to {}", warFile, this.locations
//            deploy warFile
//            log.debug "Deployed {} to {}", warFile, this.locations
//        }
    }

    @Override
    public void stop() {
    	super.stop()
        // zero our workrate derived workrates.
        // TODO might not be enough, as policy may still be executing and have a record of historic vals; should remove policies
		// (also not sure we want this; implies more generally a responsibility for sensors to announce things when disconnected,
		// vs them just showing the last known value...)
        setAttribute(REQUESTS_PER_SECOND, 0)
        setAttribute(AVG_REQUESTS_PER_SECOND, 0)
    }
}
