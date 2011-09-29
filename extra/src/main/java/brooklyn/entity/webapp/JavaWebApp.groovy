package brooklyn.entity.webapp

import brooklyn.enricher.RollingTimeWindowMeanEnricher
import brooklyn.enricher.TimeWeightedDeltaEnricher
import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.util.internal.Repeater
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import static java.util.concurrent.TimeUnit.SECONDS

/**
* An {@link Entity} representing a single java web application server instance.
*/
public abstract class JavaWebApp extends JavaApp {
    public static final Logger log = LoggerFactory.getLogger(JavaWebApp.class)

    public static final BasicConfigKey<String> WAR = [ String, "war", "Path of WAR file to deploy" ]
    public static final BasicConfigKey<List<String>> RESOURCES = [ List, "resources", "List of names of resources to copy to run directory" ]

    public static final ConfiguredAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT

    public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "webpp.reqs.processing.max", "Max processing time" ]
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "webapp.reqs.total", "Request count" ]
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "webapp.reqs.processing.time", "Total processing time" ]

    /**
     * This is the normalised req/second based on a delta of the last request count.
     */
    public static final BasicAttributeSensor<Double> REQUESTS_PER_SECOND = [ Double, "webapp.reqs.persec.last", "Reqs/Sec" ]

    public static final Integer AVG_REQUESTS_PER_SECOND_PERIOD = 10*1000
    public static final BasicAttributeSensor<Double> AVG_REQUESTS_PER_SECOND = [ Double,
        "webapp.reqs.persec.avg.$AVG_REQUESTS_PER_SECOND_PERIOD", "Average Reqs/Sec (over the last ${AVG_REQUESTS_PER_SECOND_PERIOD}ms)" ]

    public static final BasicAttributeSensor<String> ROOT_URL = [ String, "webapp.url", "URL" ]
    public static final BasicAttributeSensor<String> HTTP_SERVER = [ String, "webapp.http.server", "Server name" ]
    public static final BasicAttributeSensor<Integer> HTTP_STATUS = [ Integer, "webapp.http.status", "HTTP response code for the server" ]

    transient HttpSensorAdapter httpAdapter

    Map environment = [:]
    
    // Set to false to prevent HTTP_SERVER and HTTP_STATUS being updated (useful for integration tests)
    boolean pollForHttpStatus = true

    public JavaWebApp(Map flags=[:], Entity owner=null) {
        super(flags, owner)

        setConfigIfValNonNull(HTTP_PORT.configKey, flags.httpPort)
        setConfigIfValNonNull(WAR, flags.war)
        
        setAttribute(SERVICE_STATUS, "uninitialized")
    }
    
    protected void waitForHttpPort() {
        boolean status = new Repeater("Wait for valid HTTP status (200 or 404)")
            .repeat()
            .every(1,SECONDS)
            .until {
                Integer response = getAttribute(HTTP_STATUS)
                return (response == 200 || response == 404)
             }
            .limitIterationsTo(30)
            .run()

        if (!status) {
            throw new EntityStartException("HTTP service on port "+getAttribute(HTTP_PORT)+" failed")
        }
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(HTTP_PORT.configKey)) result.add(getConfig(HTTP_PORT.configKey))
        return result
    }

    public void initHttpSensors() {
        httpAdapter = new HttpSensorAdapter(this)
        if (pollForHttpStatus) {
            def host = getAttribute(HOSTNAME)
            def port = getAttribute(HTTP_PORT)
            attributePoller.addSensor(HTTP_STATUS, httpAdapter.newStatusValueProvider("http://${host}:${port}/"))
            attributePoller.addSensor(HTTP_SERVER, httpAdapter.newHeaderValueProvider("http://${host}:${port}/", "Server"))
            waitForHttpPort()
        }
        addHttpSensors()
    }

    public void addHttpSensors() { }

    @Override
    public void postStart() {
        log.debug "started $this: httpPort {}, host {} and jmxPort {}",
                getAttribute(HTTP_PORT), getAttribute(HOSTNAME), getAttribute(JMX_PORT)

        // TODO Want to wire this up so doesn't go through SubscriptionManager;
        // but that's an optimisation we'll do later.
        addEnricher(TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(this, REQUEST_COUNT, REQUESTS_PER_SECOND))
        addEnricher(new RollingTimeWindowMeanEnricher<Double>(this, REQUESTS_PER_SECOND, AVG_REQUESTS_PER_SECOND,
            AVG_REQUESTS_PER_SECOND_PERIOD))

        initHttpSensors()

        def warFile = getConfig(WAR)
        if (warFile) {
            log.debug "Deploying {} to {}", warFile, this.locations
            deploy warFile
            log.debug "Deployed {} to {}", warFile, this.locations
        }
    }

    @Override
    public void preStop() {
        // zero our workrate derived workrates.
        // TODO might not be enough, as policy is still executing and has a record of historic vals; should remove policies
        setAttribute(REQUESTS_PER_SECOND, 0)
        setAttribute(AVG_REQUESTS_PER_SECOND, 0)
    }
}
