package brooklyn.event.feed.http;

import java.net.URI;

import org.apache.http.impl.client.DefaultHttpClient;

import brooklyn.util.http.HttpTool;

import com.google.common.collect.ImmutableMap;

/**
 * @deprecated since 0.7; use {@link HttpTool}
 */
@Deprecated
public class HttpPolls {

    public static HttpPollValue executeSimpleGet(URI uri) {
        return HttpTool.httpGet(new DefaultHttpClient(), uri, ImmutableMap.<String,String>of());
    }
    
}
