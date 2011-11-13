package brooklyn.event.adapter;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.util.internal.StringEscapeUtils


protected class HttpPollHelper extends AbstractPollHelper {
	public static final Logger log = LoggerFactory.getLogger(HttpPollHelper.class);
	
	final HttpSensorAdapter adapter;
	
	public HttpPollHelper(HttpSensorAdapter adapter) {
		super(adapter);
		this.adapter = adapter;
	}
	
	HttpURLConnection getConnection() {
		if (adapter.isPost) throw new UnsupportedOperationException("when you need POST please implement it!")
		String url = adapter.baseUrl;
		if (adapter.urlVars) {
			url += "?"+adapter.urlVars.collect({k,v -> StringEscapeUtils.escapeHttpUrl(k)+(v!=null?"="+StringEscapeUtils.escapeHttpUrl(v):"")}).join("&")
		}
		return new URL(url).openConnection()
	}
	
	@Override
	protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) { response?.content }
	
	@Override
	AbstractSensorEvaluationContext executePollOnSuccess() {
		log.debug "http polling for {} sensors at {}", adapter.entity, adapter.baseUrl+adapter.urlVars
		HttpURLConnection connection = getConnection();
		connection.connect()
		def result = new HttpResponseContext(connection)
		log.debug "http poll for {} got: {}", adapter.entity, result.content
		return result
	}
	
	@Override
	AbstractSensorEvaluationContext executePollOnError(Exception e) {
		try {
			HttpURLConnection connection = getConnection();
			//don't attempt to connect
			return new HttpResponseContext(connection, e)
		} catch (Exception e2) {
			return null
		}
	}
}
