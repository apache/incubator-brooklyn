package brooklyn.event.adapter;

import groovy.json.JsonSlurper

import java.util.Map

import javax.annotation.Nullable

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.Sensor
import brooklyn.event.adapter.AbstractSensorAdapter.SensorEvaluationContext

/** context object for evaluating sensor closures with http data handy */
public class HttpResponseContext implements SensorEvaluationContext {
	
	public static final Logger log = LoggerFactory.getLogger(HttpResponseContext.class);
	
	/** may be null during testing */
	@Nullable
	final HttpURLConnection connection;
	
	/** http result code, or -1 if error is set */
	final int resultCode;
	/** http return headers, where all values are lists (as per HttpConnection) see "headers" for direct access to non-list headers */
	final Map<String,List<String>> headerLists;
	/** exception returned by call, or null if no error */
	@Nullable
	final Exception error;
	
	/** may be null if no content available */
	@Nullable
	public final String content;

	/** usual constructor */	
	public HttpResponseContext(HttpURLConnection conn) {
		this(conn, conn.getResultCode(), conn.getHeaderFields(), conn.getContent().readLines().join("\n"), null)
	}
	/** constructor for when there is an error; note that many of the methods on connection will throw errors */
	public HttpResponseContext(HttpURLConnection conn, Exception error) {
		this(conn, -1, [:], null, error)
	}
	/** constructor for testing of non-connection usage */
	public HttpResponseContext(HttpURLConnection conn, int resultCode, Map headers, String content, Exception error) {
		this.connection = conn;
		this.resultCode = resultCode;
		this.headerLists = headers;
		this.content = content;
		this.error = error;
	}

	def propertyMissing(String name) {
		if (extraProperties.containsKey(name)) return extraProperties.get(name)
		else throw new MissingPropertyException(name)
	}
	final Map extraProperties=[:]
	
	public Object evaluate(Entity entity, Sensor sensor, Closure c) {
		evaluate(entity:entity, sensor:sensor, c)
	}
	public Object evaluate(Map properties=[:], Closure c) {
		try {
			extraProperties << properties
			c.setDelegate(this)
			c.setResolveStrategy(Closure.DELEGATE_FIRST)
			return c.call(connection)
		} catch (Exception e) {
			if (error) {
				log.debug "unable to evaluate sensor {} because call had an error ({}): {}", properties, error, e
				return UNSET;
			}
			else throw e;
		} finally {
			extraProperties.clear()
		}
	}

	private transient Map<String,Object> headers = null
	/** http return headers; values are strings in most cases, lists of strings if the original was a list with zero or 2+ values */
	public synchronized Map<String,String> getHeaders() {
		if (headers==null) {
			headers = [:]
			headerLists.each { k,v -> headers.put(k, v.size()==1 ? v.get(0) : v) }
		}
		return headers
	}
	private transient Object json;
	public synchronized Object getJson() {
		if (json==null) json = new JsonSlurper().parseText(content);
		return json
	}
	
}
