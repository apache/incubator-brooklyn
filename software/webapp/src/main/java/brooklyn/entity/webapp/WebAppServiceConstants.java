package brooklyn.entity.webapp;

import java.util.List;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

public interface WebAppServiceConstants {

    @SetFromFlag("httpPort")
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT;

    @SetFromFlag("httpsPort")
    public static final PortAttributeSensorAndConfigKey HTTPS_PORT = Attributes.HTTPS_PORT;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("enabledProtocols")
    public static final BasicAttributeSensorAndConfigKey<List<String>> ENABLED_PROTOCOLS = new BasicAttributeSensorAndConfigKey(
            List.class, "webapp.enabledProtocols", "List of enabled protocols (e.g. http, https)", ImmutableList.of("http"));

    @SetFromFlag("httpsSsl")
    public static final BasicAttributeSensorAndConfigKey<HttpsSslConfig> HTTPS_SSL_CONFIG = new BasicAttributeSensorAndConfigKey<HttpsSslConfig>(
            HttpsSslConfig.class, "webapp.https.ssl", "SSL Configuration for HTTPS", null);
    
    /** @deprecated since 0.6.0, callers configure with
     * easily configured with {@link WebAppServiceMethods#connectWebAppServerPolicies(brooklyn.entity.basic.EntityLocal, brooklyn.util.time.Duration)} */
    public static final Integer REQUESTS_PER_SECOND_WINDOW_PERIOD = 10 * 1000;

    public static final AttributeSensor<Integer> REQUEST_COUNT =
            Sensors.newIntegerSensor("webapp.reqs.total", "Request count");
    public static final brooklyn.event.basic.BasicAttributeSensor<Integer> ERROR_COUNT =
            new brooklyn.event.basic.BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.errors", "Request errors");
    public static final AttributeSensor<Integer> TOTAL_PROCESSING_TIME = Sensors.newIntegerSensor(
            "webapp.reqs.processingTime.total", "Total processing time, reported by webserver (millis)");
    public static final AttributeSensor<Integer> MAX_PROCESSING_TIME =
            Sensors.newIntegerSensor("webapp.reqs.processingTime.max", "Max processing time for any single request, reported by webserver (millis)");

    /** the fraction of time represented by the most recent delta to TOTAL_PROCESSING_TIME, ie 0.4 if 800 millis were accumulated in last 2s;
     * easily configured with {@link WebAppServiceMethods#connectWebAppServerPolicies(brooklyn.entity.basic.EntityLocal, brooklyn.util.time.Duration)} */
    public static final AttributeSensor<Double> PROCESSING_TIME_FRACTION_LAST =
            Sensors.newDoubleSensor("webapp.reqs.processingTime.fraction.last", "Fraction of time spent processing, reported by webserver (percentage, last datapoint)");
    public static final AttributeSensor<Double> PROCESSING_TIME_FRACTION_IN_WINDOW =
            Sensors.newDoubleSensor("webapp.reqs.processingTime.fraction.windowed", "Fraction of time spent processing, reported by webserver (percentage, over time window)");

    public static final AttributeSensor<Long> BYTES_RECEIVED =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.received", "Total bytes received by the webserver");
    public static final AttributeSensor<Long> BYTES_SENT =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.sent", "Total bytes sent by the webserver");

    /** req/second computed from the delta of the last request count and an associated timestamp */
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_LAST =
            Sensors.newDoubleSensor("webapp.reqs.perSec.last", "Reqs/sec (last datapoint)");

    /** rolled-up req/second for a window, 
     * easily configured with {@link WebAppServiceMethods#connectWebAppServerPolicies(brooklyn.entity.basic.EntityLocal, brooklyn.util.time.Duration)} */
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW =
            Sensors.newDoubleSensor("webapp.reqs.perSec.windowed", "Reqs/sec (over time window)");

    public static final AttributeSensor<String> ROOT_URL = RootUrl.ROOT_URL;

}

//this class is added because the ROOT_URL relies on a static initialization which unfortunately can't be added to
//an interface.
class RootUrl {
    public static final AttributeSensor<String> ROOT_URL = Sensors.newStringSensor("webapp.url", "URL");

    static {
        RendererHints.register(ROOT_URL, new RendererHints.NamedActionWithUrl("Open"));
    }
}
