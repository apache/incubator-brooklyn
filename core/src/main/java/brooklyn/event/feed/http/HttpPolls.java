package brooklyn.event.feed.http;

import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import brooklyn.util.exceptions.Exceptions;

public class HttpPolls {

    public static HttpPollValue executeSimpleGet(URI uri) {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(uri);
        try {
            long startTime = System.currentTimeMillis();
            HttpResponse httpResponse = httpClient.execute(httpGet);
            try {
                return new HttpPollValue(httpResponse, startTime);
            } finally {
                EntityUtils.consume(httpResponse.getEntity());
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
}
