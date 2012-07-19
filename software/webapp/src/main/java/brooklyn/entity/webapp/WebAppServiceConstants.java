package brooklyn.entity.webapp;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface WebAppServiceConstants {

    @SetFromFlag("httpPort")
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT;

    public static final brooklyn.event.basic.BasicAttributeSensor<Integer> ERROR_COUNT =
            new brooklyn.event.basic.BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.errors", "Request errors");
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME =
            new BasicAttributeSensor<Integer>(Integer.class, "webpp.reqs.processing.max", "Max processing time");
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.total", "Request count");
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.processing.time", "Total processing time");

    public static final BasicAttributeSensor<Long> BYTES_RECEIVED =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.received", "Total bytes received by the webserver");
    public static final BasicAttributeSensor<Long> BYTES_SENT =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.sent", "Total bytes sent by the webserver");

    /**
     * This is the normalised req/second based on a delta of the last request count.
     */
    public static final BasicAttributeSensor<Double> REQUESTS_PER_SECOND =
            new BasicAttributeSensor<Double>(Double.class, "webapp.reqs.persec.last", "Reqs/Sec");

    public static final Integer AVG_REQUESTS_PER_SECOND_PERIOD = 10 * 1000;
    public static final BasicAttributeSensor<Double> AVG_REQUESTS_PER_SECOND
            = new BasicAttributeSensor<Double>(Double.class, String.format("webapp.reqs.persec.avg.%s", AVG_REQUESTS_PER_SECOND_PERIOD),
            String.format("Average Reqs/Sec (over the last %sms)", AVG_REQUESTS_PER_SECOND_PERIOD));

    public static final BasicAttributeSensor<String> ROOT_URL = RootUrl.ROOT_URL;

//    public static final BasicAttributeSensor<String> HTTP_SERVER =
//            new BasicAttributeSensor<String>(String.class, "webapp.http.server", "Server name");
//    public static final BasicAttributeSensor<Integer> HTTP_STATUS =
//            new BasicAttributeSensor<Integer>(Integer.class, "webapp.http.status", "HTTP response code for the server");
}

//this class is added because the ROOT_URL relies on a static initialization which unfortunately can't be added to
//an interface.
class RootUrl {
    public static final BasicAttributeSensor<String> ROOT_URL = new BasicAttributeSensor<String>(String.class, "webapp.url", "URL");

    static {
        RendererHints.register(ROOT_URL, new RendererHints.NamedActionWithUrl("Open"));
    }
}
