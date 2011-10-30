package brooklyn.event.adapter

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.adapter.AbstractSensorAdapter.SensorEvaluationContext
import brooklyn.util.flags.FlagUtils;

public class HttpUrlSensorAdapter extends AbstractSensorAdapter {

	public static final Logger log = LoggerFactory.getLogger(HttpUrlSensorAdapter.class)

	String baseUrl;
	Map urlVars=[:]

	public HttpUrlSensorAdapter(Map flags=[:], String url) {
		super(flags);
		this.baseUrl = url;
	}

	// TODO does not currenlty support post or parameter conveniences ... but 'suburl' below shows how this could work
	/** returns new adapter which will POST the vars */
	public HttpUrlSensorAdapter post() { throw new UnsupportedOperationException("when you need this please write it!") }
	/** returns a new adapter, registered, with the given additional parameters (for 'get' or 'post') */ 
	public HttpUrlSensorAdapter vars(Map vars) { throw new UnsupportedOperationException("when you need this please write it!") }

	/** returns a new adapter, registered, for accessing a child URL */
	public HttpUrlSensorAdapter suburl(Map flags=[:], String urlExtension) {
		def newFlags = FlagUtils.getFieldsWithValues(this)+flags
		def newUrl = baseUrl;
		if (newUrl.endsWith("/") && urlExtension.startsWith("/")) newUrl = newUrl.substring(0, newUrl.length()-1);
		newUrl += urlExtension
		HttpUrlSensorAdapter ad2 = new HttpUrlSensorAdapter(newFlags, newUrl);
		if (registry) registry.register(ad2)
		return ad2
	}

	void executePoll() {
		HttpResponseContext response;
		try {
			HttpURLConnection connection = baseUrl.openConnection()
			connection.connect()
			response = new HttpResponseContext(connection)
		} catch (Exception e) {
			log.warn "error reading ${this}", e
			response = new HttpResponseContext(connection, e)
		}
	}

}
