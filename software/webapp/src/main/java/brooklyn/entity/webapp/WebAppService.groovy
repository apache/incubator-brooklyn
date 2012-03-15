package brooklyn.entity.webapp;

import java.util.List

import brooklyn.enricher.RollingTimeWindowMeanEnricher
import brooklyn.enricher.TimeWeightedDeltaEnricher
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.util.flags.SetFromFlag

public interface WebAppService {

	//FIXME not used?
    public static final BasicConfigKey<List<String>> RESOURCES = [ List, "resources", "List of names of resources to copy to run directory" ]

    @SetFromFlag("httpPort")
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT

    public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "webpp.reqs.processing.max", "Max processing time" ]
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "webapp.reqs.total", "Request count" ]
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "webapp.reqs.processing.time", "Total processing time" ]

	public static final BasicAttributeSensor<Long> BYTES_RECEIVED =
		[ Long, "webapp.reqs.bytes.received", "Total bytes received by the webserver" ]
	public static final BasicAttributeSensor<Long> BYTES_SENT =
		[ Long, "webapp.reqs.bytes.sent", "Total bytes sent by the webserver" ]

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

	public static class Utils implements WebAppService {
		public static void connectWebAppServerPolicies(EntityLocal entity) {
			entity.addEnricher(TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(entity, REQUEST_COUNT, REQUESTS_PER_SECOND))
			entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity, REQUESTS_PER_SECOND, AVG_REQUESTS_PER_SECOND,
					AVG_REQUESTS_PER_SECOND_PERIOD))
		}
    }
	
}
