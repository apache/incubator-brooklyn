package brooklyn.entity.webapp;

import java.util.List;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

public interface WebAppServiceConstants {

    @SetFromFlag("httpPort")
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT;

    @SetFromFlag("httpsPort")
    public static final PortAttributeSensorAndConfigKey HTTPS_PORT = Attributes.HTTPS_PORT;

    @SetFromFlag("enabledProtocols")
    public static final BasicAttributeSensorAndConfigKey<List<String>> ENABLED_PROTOCOLS = new BasicAttributeSensorAndConfigKey(
            List.class, "webapp.enabledProtocols", "List of enabled protocols (e.g. http, https)", ImmutableList.of("http"));

    @SetFromFlag("httpsSsl")
    public static final BasicAttributeSensorAndConfigKey<HttpsSslConfig> HTTPS_SSL_CONFIG = new BasicAttributeSensorAndConfigKey<HttpsSslConfig>(
            HttpsSslConfig.class, "webapp.https.ssl", "SSL Configuration for HTTPS", null);
    
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.total", "Request count");
    public static final brooklyn.event.basic.BasicAttributeSensor<Integer> ERROR_COUNT =
            new brooklyn.event.basic.BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.errors", "Request errors");
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.processingTime.total", "Total processing time (millis)");
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.processingTime.max", "Max processing time (millis)");

    public static final BasicAttributeSensor<Long> BYTES_RECEIVED =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.received", "Total bytes received by the webserver");
    public static final BasicAttributeSensor<Long> BYTES_SENT =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.sent", "Total bytes sent by the webserver");

    /** req/second computed from the delta of the last request count and an associated timestamp */
    public static final BasicAttributeSensor<Double> REQUESTS_PER_SECOND_LAST =
            new BasicAttributeSensor<Double>(Double.class, "webapp.reqs.perSec.last", "Reqs/sec (last datapoint)");
    /** @deprecated since 0.5.0, use REQUESTS_PER_SECOND_LAST */
    public static final BasicAttributeSensor<Double> REQUESTS_PER_SECOND = REQUESTS_PER_SECOND_LAST;

    // TODO make this a config key
    public static final Integer REQUESTS_PER_SECOND_WINDOW_PERIOD = 10 * 1000;
    /** rolled-up req/second for a window */
    public static final BasicAttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW
            = new BasicAttributeSensor<Double>(Double.class, String.format("webapp.reqs.perSec.windowed", REQUESTS_PER_SECOND_WINDOW_PERIOD),
                    String.format("Reqs/sec (over time window)", REQUESTS_PER_SECOND_WINDOW_PERIOD));
    /** @deprecated since 0.5.0, use REQUESTS_PER_SECOND_WINDOW_PERIOD */
    public static final Integer AVG_REQUESTS_PER_SECOND_PERIOD = REQUESTS_PER_SECOND_WINDOW_PERIOD;
    /** @deprecated since 0.5.0, use REQUESTS_PER_SECOND_IN_WINDOW */
    public static final BasicAttributeSensor<Double> AVG_REQUESTS_PER_SECOND = REQUESTS_PER_SECOND_IN_WINDOW;

    public static final BasicAttributeSensor<String> ROOT_URL = RootUrl.ROOT_URL;

}

//this class is added because the ROOT_URL relies on a static initialization which unfortunately can't be added to
//an interface.
class RootUrl {
    public static final BasicAttributeSensor<String> ROOT_URL = new BasicAttributeSensor<String>(String.class, "webapp.url", "URL");

    static {
        RendererHints.register(ROOT_URL, new RendererHints.NamedActionWithUrl("Open"));
    }
}
