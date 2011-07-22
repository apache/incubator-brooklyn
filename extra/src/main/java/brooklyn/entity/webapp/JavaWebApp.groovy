package brooklyn.entity.webapp

import java.util.Collection;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.ConfigKeys
import brooklyn.entity.basic.JavaApp
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.policy.RollingTimeWindowMeanEnricher
import brooklyn.policy.TimeWeightedDeltaEnricher
import brooklyn.policy.RollingTimeWindowMeanEnricher.ConfidenceQualifiedNumber

/**
* An {@link brooklyn.entity.Entity} representing a single java web application server instance.
*/
public abstract class JavaWebApp extends JavaApp {
    public static final Logger log = LoggerFactory.getLogger(JavaWebApp.class)

    public static final BasicConfigKey<String> WAR = [ String, "war", "Path of WAR file to deploy" ]
    public static final ConfigKey<Integer> SUGGESTED_HTTP_PORT = ConfigKeys.SUGGESTED_HTTP_PORT

    public static final AttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT
    public static final Integer AVG_REQUESTS_PER_SECOND_PERIOD = 30*1000

    public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "webpp.reqs.processing.max", "Max processing time" ]
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "webapp.reqs.total", "Request count" ]
    // this is the normalised req/second based on a delta of the last request count, e.g the derived req/s for the last time period
    public static final BasicAttributeSensor<Double> REQUESTS_PER_SECOND = [ Double, "webapp.reqs.persec.last", "Reqs/Sec" ]
    // FIXME: this value can actually go stale when we aren't receiving request count messages 
    public static final BasicAttributeSensor<ConfidenceQualifiedNumber> AVG_REQUESTS_PER_SECOND = [ ConfidenceQualifiedNumber, 
        "webapp.reqs.persec.avg.$AVG_REQUESTS_PER_SECOND_PERIOD", "Average Reqs/Sec (over the last ${AVG_REQUESTS_PER_SECOND_PERIOD}ms)" ]
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "webapp.reqs.processing.time", "Total processing time" ]

    public static final BasicAttributeSensor<String> ROOT_URL = [ String, "webapp.url", "URL" ]
    public static final BasicAttributeSensor<String> HTTP_SERVER = [ String, "webapp.http.server", " Server name" ]
    public static final BasicAttributeSensor<Integer> HTTP_STATUS = [ Integer, "webapp.http.status", " HTTP response code for the server" ]

    transient HttpSensorAdapter httpAdapter

    public JavaWebApp(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        if (properties.httpPort) setConfig(SUGGESTED_HTTP_PORT, properties.remove("httpPort"))

        setAttribute(SERVICE_STATUS, "uninitialized")
    }

    protected abstract void waitForHttpPort();

    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(SUGGESTED_HTTP_PORT)) result.add(getConfig(SUGGESTED_HTTP_PORT))
        return result
    }

    public void initHttpSensors() {
        def host = getAttribute(JMX_HOST)
        def port = getAttribute(HTTP_PORT)
        attributePoller.addSensor(HTTP_STATUS, httpAdapter.newStatusValueProvider("http://${host}:${port}/"))
        attributePoller.addSensor(HTTP_SERVER, httpAdapter.newHeaderValueProvider("http://${host}:${port}/", "Server"))
    }

    @Override
    public void start(Collection<Location> locations) {
        super.start(locations)

        log.debug "started $this: httpPort {}, jmxHost {} and jmxPort {}",
                getAttribute(HTTP_PORT), getAttribute(JMX_HOST), getAttribute(JMX_PORT)

        // TODO Want to wire this up so doesn't go through SubscriptionManager;
        // but that's an optimisation we'll do later.
        addPolicy(TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(this, REQUEST_COUNT, REQUESTS_PER_SECOND))
        addPolicy(new RollingTimeWindowMeanEnricher<Double>(this, REQUESTS_PER_SECOND, AVG_REQUESTS_PER_SECOND,
            AVG_REQUESTS_PER_SECOND_PERIOD))

        waitForHttpPort()
//        initHttpSensors()

        if (getConfig(WAR)) {
            log.debug "Deploying {} to {}", getConfig(WAR), this.locations
            deploy(getConfig(WAR))
            log.debug "Deployed {} to {}", getConfig(WAR), this.locations
        }
    }

    public void deploy(String file) {
        getSshBasedSetup(locations.find({ it instanceof MachineLocation })).deploy(new File(file))
    }

}

