package brooklyn.event.feed.http;

import java.net.URI;
import java.util.Map;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.FeedConfig;
import brooklyn.event.feed.PollConfig;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.URLParamEncoder;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;

public class HttpPollConfig<T> extends PollConfig<HttpPollValue, T, HttpPollConfig<T>> {

    private String method = "GET";
    private String suburl = "";
    private Map<String, String> vars = ImmutableMap.<String,String>of();
    private Map<String, String> headers = ImmutableMap.<String,String>of();
    private byte[] body;

    public static final Predicate<HttpPollValue> DEFAULT_SUCCESS = new Predicate<HttpPollValue>() {
        @Override
        public boolean apply(@Nullable HttpPollValue input) {
            return input != null && input.getResponseCode() >= 200 && input.getResponseCode() <= 399;
        }};
    
    public HttpPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        super.checkSuccess(DEFAULT_SUCCESS);
    }

    public HttpPollConfig(HttpPollConfig<T> other) {
        super(other);
        suburl = other.suburl;
        vars = other.vars;
        method = other.method;
        headers = other.headers;
    }
    
    public String getSuburl() {
        return suburl;
    }
    
    public Map<String, String> getVars() {
        return vars;
    }
    
    public String getMethod() {
        return method;
    }
    
    public byte[] getBody() {
        return body;
    }
    
    public HttpPollConfig<T> method(String val) {
        this.method = val; return this;
    }
    
    public HttpPollConfig<T> suburl(String val) {
        this.suburl = val; return this;
    }
    
    public HttpPollConfig<T> vars(Map<String,String> val) {
        this.vars = val; return this;
    }
    
    public HttpPollConfig<T> headers(Map<String,String> val) {
        this.headers = val; return this;
    }
    
    public HttpPollConfig<T> body(byte[] val) {
        this.body = val; return this;
    }
    
    public URI buildUri(URI baseUri, Map<String,String> baseUriVars) {
        String uri = (baseUri != null ? baseUri.toString() : "") + (suburl != null ? suburl : "");
        Map<String,String> allvars = concat(baseUriVars, vars);
        
        if (allvars != null && allvars.size() > 0) {
            Iterable<String> args = Iterables.transform(allvars.entrySet(), 
                    new Function<Map.Entry<String,String>,String>() {
                        @Override public String apply(Map.Entry<String,String> entry) {
                            String k = entry.getKey();
                            String v = entry.getValue();
                            return URLParamEncoder.encode(k) + (v != null ? "=" + URLParamEncoder.encode(v) : "");
                        }
                    });
            uri += "?" + Joiner.on("&").join(args);
        }
        
        return URI.create(uri);
    }

    public Map<String, String> buildHeaders(Map<String, String> baseHeaders) {
        return MutableMap.<String,String>builder()
                .putAll(baseHeaders)
                .putAll(headers)
                .build();
    }
    
    @SuppressWarnings("unchecked")
    private <K,V> Map<K,V> concat(Map<? extends K,? extends V> map1, Map<? extends K,? extends V> map2) {
        if (map1 == null || map1.isEmpty()) return (Map<K,V>) map2;
        if (map2 == null || map2.isEmpty()) return (Map<K,V>) map1;
        
        // TODO Not using Immutable builder, because that fails if duplicates in map1 and map2
        return MutableMap.<K,V>builder().putAll(map1).putAll(map2).build();
    }

}
