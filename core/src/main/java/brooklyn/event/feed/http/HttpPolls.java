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
        HttpResponse httpResponse = null;
        try {
            try {
                long startTime = System.currentTimeMillis();
                httpResponse = httpClient.execute(httpGet);
                return new HttpPollValue(httpResponse, startTime);
            } finally {
                if (httpResponse!=null)
                    EntityUtils.consume(httpResponse.getEntity());
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
}
