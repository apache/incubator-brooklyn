package brooklyn.event.adapter;

import org.slf4j.Logger

import org.slf4j.LoggerFactory

import brooklyn.util.crypto.SslTrustUtils
import brooklyn.util.text.StringEscapes

/**
 * @deprecated See brooklyn.event.feed.http.HttpFeed
 */
@Deprecated
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
                StringEscapes.escapeUrlParam(k.toString()) +
                        (v != null ? "=" + StringEscapes.escapeUrlParam(v.toString()) : "")
            }
            url += "?" + args.join("&")
        }
        return new URL(url).openConnection();
    }

    @Override
    protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) {
        response?.content
    }

    @Override
    AbstractSensorEvaluationContext executePollOnSuccess() {
        if (log.isTraceEnabled()) log.trace("http polling for {} sensors at {}", adapter.entity, adapter.baseUrl+" "+adapter.urlVars);
        HttpURLConnection connection = getConnection();
        SslTrustUtils.trustAll(connection);
        connection.connect();
        def result = new HttpResponseContext(connection);
        if (log.isTraceEnabled()) log.trace("http poll for {} returned status {}", adapter.entity, result.responseCode);
        return result;
    }

    @Override
    AbstractSensorEvaluationContext executePollOnError(Exception e) {
        try {
            HttpURLConnection connection = getConnection();
            //don't attempt to connect
            return new HttpResponseContext(connection, e);
        } catch (Exception e2) {
            return null;
        }
    }
}
