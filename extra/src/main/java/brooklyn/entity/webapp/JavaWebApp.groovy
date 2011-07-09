package brooklyn.entity.webapp

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.ConfigKeys
import brooklyn.entity.basic.JavaApp
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation

/**
* An {@link brooklyn.entity.Entity} representing a single java web application server instance.
*/
public abstract class JavaWebApp extends JavaApp {
    public static final Logger log = LoggerFactory.getLogger(JavaWebApp.class)

    public static final BasicConfigKey<String> WAR = [ String, "war", "Path of WAR file to deploy" ]
    public static final ConfigKey<Integer> SUGGESTED_HTTP_PORT = ConfigKeys.SUGGESTED_HTTP_PORT

    public static final AttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT

    public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "webpp.reqs.processing.max", "Max processing time" ]
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "webapp.reqs.total", "Request count" ]
    public static final BasicAttributeSensor<Double> REQUESTS_PER_SECOND = [ Double, "webapp.reqs.persec", "Reqs/Sec" ]
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "webapp.reqs.processing.time", "Total processing time" ]

    public JavaWebApp(Map properties=[:]) {
        super(properties)
        if (properties.httpPort) setConfig(SUGGESTED_HTTP_PORT, properties.remove("httpPort"))

        setAttribute(NODE_UP, false)
        setAttribute(NODE_STATUS, "uninitialized")
    }

    protected abstract void waitForHttpPort();

    }

    @Override
    public void start(Collection<Location> locations) {
        super.start(locations)

        log.debug "started $this: httpPort {}, jmxHost {} and jmxPort {}",
                getAttribute(HTTP_PORT), getAttribute(JMX_HOST), getAttribute(JMX_PORT)

        attributePoller.addSensor(REQUESTS_PER_SECOND, { computeReqsPerSec() } as ValueProvider, 1000L)

        waitForHttpPort()

        if (getConfig(WAR)) {
            log.debug "Deploying {} to {}", getConfig(WAR), this.locations
            deploy(getConfig(WAR))
            log.debug "Deployed {} to {}", getConfig(WAR), this.locations
        }
    }

    public void deploy(String file) {
        getSshBasedSetup(locations.find({ it instanceof MachineLocation })).deploy(new File(file))
    }

    protected double computeReqsPerSec() {
        def curTimestamp = System.currentTimeMillis()
        def curCount = getAttribute(REQUEST_COUNT) ?: 0
        def prevTimestamp = tempWorkings['tmp.reqs.timestamp'] ?: 0
        def prevCount = tempWorkings['tmp.reqs.count'] ?: 0
        tempWorkings['tmp.reqs.timestamp'] = curTimestamp
        tempWorkings['tmp.reqs.count'] = curCount
        log.trace "previous data {} at {}, current {} at {}", prevCount, prevTimestamp, curCount, curTimestamp

        // Calculate requests per second
        double diff = curCount - prevCount
        long dt = curTimestamp - prevTimestamp
        double result

        if (dt <= 0 || dt > 60*1000) {
            result = -1;
        } else {
            result = ((double) 1000.0 * diff) / dt
        }
        log.trace "computed $result reqs/sec over $dt millis"
        return result
    }
}

