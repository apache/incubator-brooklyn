package brooklyn.event.adapter;

import javax.net.ssl.HttpsURLConnection

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.util.internal.StringEscapeUtils
import brooklyn.util.internal.TrustingSslSocketFactory;

protected class HttpPollHelper extends AbstractPollHelper {
	public static final Logger log = LoggerFactory.getLogger(HttpPollHelper.class);
	
	final HttpSensorAdapter adapter;
	
	public HttpPollHelper(HttpSensorAdapter adapter) {
		super(adapter);
		this.adapter = adapter;
	}
	
	private HttpURLConnection getConnection() {
		if (adapter.isPost) throw new UnsupportedOperationException("when you need POST please implement it here!")
		String url = adapter.baseUrl;
		if (adapter.urlVars) {
			def args = adapter.urlVars.collect { k,v ->
                StringEscapeUtils.escapeHttpUrl(k.toString()) +
                    (v != null ? "=" + StringEscapeUtils.escapeHttpUrl(v.toString()) : "")
			}
            url += "?" + args.join("&")
		}
		return new URL(url).openConnection()
	}
	
	@Override
	protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) {
        response?.content
    }
	
	@Override
	AbstractSensorEvaluationContext executePollOnSuccess() {
        if (log.isDebugEnabled()) log.debug "http polling for {} sensors at {}", adapter.entity, adapter.baseUrl+" "+adapter.urlVars
		HttpURLConnection connection = getConnection();
        if (connection in HttpsURLConnection) {
            ((HttpsURLConnection)connection).setSSLSocketFactory(TrustingSslSocketFactory.INSTANCE);
        }
		connection.connect()
		def result = new HttpResponseContext(connection)
        if (log.isDebugEnabled()) log.debug "http poll for {} got: {}", adapter.entity, result.content
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
