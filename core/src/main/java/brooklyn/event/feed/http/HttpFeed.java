package brooklyn.event.feed.http;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Provides a feed of attribute values, by polling over http.
 * 
 * Example usage (e.g. in an entity that extends SoftwareProcessImpl):
 * <pre>
 * {@code
 * private HttpFeed feed;
 * 
 * //@Override
 * protected void connectSensors() {
 *   super.connectSensors();
 *   
 *   feed = HttpFeed.builder()
 *       .entity(this)
 *       .period(200)
 *       .baseUri(String.format("http://%s:%s/management/subsystem/web/connector/http/read-resource", host, port))
 *       .baseUriVars(ImmutableMap.of("include-runtime","true"))
 *       .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
 *           .onSuccess(HttpValueFunctions.responseCodeEquals(200))
 *           .onError(Functions.constant(false)))
 *       .poll(new HttpPollConfig<Integer>(REQUEST_COUNT)
 *           .onSuccess(HttpValueFunctions.jsonContents("requestCount", Integer.class)))
 *       .build();
 * }
 * 
 * {@literal @}Override
 * protected void disconnectSensors() {
 *   super.disconnectSensors();
 *   if (feed != null) feed.stop();
 * }
 * }
 * </pre>
 * <p>
 *  
 * This also supports giving a Supplier for the URL 
 * (e.g. {@link Entities#supplier(brooklyn.entity.Entity, brooklyn.event.AttributeSensor)})
 * from a sensor.  Note however that if a Supplier-based sensor is *https*,
 * https-specific initialization may not occur if the URL is not available at start time,
 * and it may report errors if that sensor is not available.
 * Some guidance for controlling enablement of a feed based on availability of a sensor
 * can be seen in HttpLatencyDetector (in brooklyn-policy). 
 * 
 * @author aled
 */
public class HttpFeed extends AbstractFeed {

    public static final Logger log = LoggerFactory.getLogger(HttpFeed.class);

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private EntityLocal entity;
        private Supplier<URI> baseUriProvider;
        private long period = 500;
        private TimeUnit periodUnits = TimeUnit.MILLISECONDS;
        private List<HttpPollConfig<?>> polls = Lists.newArrayList();
        private URI baseUri;
        private Map<String, String> baseUriVars = Maps.newLinkedHashMap();
        private Map<String, String> headers = Maps.newLinkedHashMap();
        private boolean suspended = false;
        private volatile boolean built;
        
        public Builder entity(EntityLocal val) {
            this.entity = val;
            return this;
        }
        public Builder baseUri(Supplier<URI> val) {
            if (baseUri!=null && val!=null)
                throw new IllegalStateException("Builder cannot take both a URI and a URI Provider");
            this.baseUriProvider = val;
            return this;
        }
        public Builder baseUri(URI val) {
            if (baseUriProvider!=null && val!=null)
                throw new IllegalStateException("Builder cannot take both a URI and a URI Provider");
            this.baseUri = val;
            return this;
        }
        public Builder baseUrl(URL val) {
            return baseUri(URI.create(val.toString()));
        }
        public Builder baseUri(String val) {
            return baseUri(URI.create(val));
        }
        public Builder baseUriVars(Map<String,String> vals) {
            baseUriVars.putAll(vals);
            return this;
        }
        public Builder baseUriVar(String key, String val) {
            baseUriVars.put(key, val);
            return this;
        }
        public Builder headers(Map<String,String> vals) {
            headers.putAll(vals);
            return this;
        }
        public Builder header(String key, String val) {
            headers.put(key, val);
            return this;
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            this.period = val;
            this.periodUnits = units;
            return this;
        }
        public Builder poll(HttpPollConfig<?> config) {
            polls.add(config);
            return this;
        }
        public Builder suspended() {
            return suspended(true);
        }
        public Builder suspended(boolean startsSuspended) {
            this.suspended = startsSuspended;
            return this;
        }
        public HttpFeed build() {
            built = true;
            HttpFeed result = new HttpFeed(this);
            if (suspended) result.suspend();
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("HttpFeed.Builder created, but build() never called");
        }
    }
    
    private static class HttpPollIdentifier {
        final String method;
        final Supplier<URI> uriProvider;
        final Map<String,String> headers;
        final byte[] body;

        private HttpPollIdentifier(String method, URI uri, Map<String,String> headers, byte[] body) {
            this(method, Suppliers.ofInstance(uri), headers, body);
        }
        private HttpPollIdentifier(String method, Supplier<URI> uriProvider, Map<String,String> headers, byte[] body) {
            this.method = checkNotNull(method, "method").toLowerCase();
            this.uriProvider = checkNotNull(uriProvider, "uriProvider");
            this.headers = checkNotNull(headers, "headers");
            this.body = body;
            
            if (!(this.method.equals("get") || this.method.equals("post"))) {
                throw new IllegalArgumentException("Unsupported HTTP method (only supports GET and POST): "+method);
            }
            if (body != null && method.equalsIgnoreCase("get")) {
                throw new IllegalArgumentException("Must not set body for http GET method");
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(method, uriProvider, headers, body);
        }
        
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof HttpPollIdentifier)) {
                return false;
            }
            HttpPollIdentifier o = (HttpPollIdentifier) other;
            return Objects.equal(method, o.method) &&
                    Objects.equal(uriProvider, o.uriProvider) &&
                    Objects.equal(headers, o.headers) &&
                    Objects.equal(body, o.body);
        }
    }
    
    // Treat as immutable once built
    private final SetMultimap<HttpPollIdentifier, HttpPollConfig<?>> polls = HashMultimap.<HttpPollIdentifier,HttpPollConfig<?>>create();
    
    protected HttpFeed(Builder builder) {
        super(builder.entity);
        Map<String,String> baseHeaders = ImmutableMap.copyOf(checkNotNull(builder.headers, "headers"));
        
        for (HttpPollConfig<?> config : builder.polls) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            HttpPollConfig<?> configCopy = new HttpPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            String method = config.getMethod();
            Map<String,String> headers = config.buildHeaders(baseHeaders);
            byte[] body = config.getBody();
            
            Supplier<URI> baseUriProvider = builder.baseUriProvider;
            if (builder.baseUri!=null) {
                if (baseUriProvider!=null)
                    throw new IllegalStateException("Not permitted to supply baseUri and baseUriProvider");
                Map<String,String> baseUriVars = ImmutableMap.copyOf(checkNotNull(builder.baseUriVars, "baseUriVars"));
                URI uri = config.buildUri(builder.baseUri, baseUriVars);
                baseUriProvider = Suppliers.ofInstance(uri);
            } else if (!builder.baseUriVars.isEmpty()) {
                throw new IllegalStateException("Not permitted to supply URI vars when using a URI provider");
            }
            checkNotNull(baseUriProvider);

            polls.put(new HttpPollIdentifier(method, baseUriProvider, headers, body), configCopy);
        }
    }

    @Override
    protected void preStart() {
        for (final HttpPollIdentifier pollInfo : polls.keySet()) {
            Set<HttpPollConfig<?>> configs = polls.get(pollInfo);
            long minPeriod = Integer.MAX_VALUE;
            Set<AttributePollHandler<? super HttpPollValue>> handlers = Sets.newLinkedHashSet();

            for (HttpPollConfig<?> config : configs) {
                handlers.add(new AttributePollHandler<HttpPollValue>(config, entity, this));
                if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
            }
            
            final DefaultHttpClient httpClient = new DefaultHttpClient();
            URI uri = pollInfo.uriProvider.get();
            // TODO if supplier returns null, we may wish to defer initialization until url available?
            if (uri!=null && "https".equalsIgnoreCase(uri.getScheme())) {
                try {
                    int port = (uri.getPort() >= 0) ? uri.getPort() : 443;
                    SSLSocketFactory socketFactory = new SSLSocketFactory(new TrustAllStrategy(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                    Scheme sch = new Scheme("https", port, socketFactory);
                    httpClient.getConnectionManager().getSchemeRegistry().register(sch);
                } catch (Exception e) {
                    log.warn("Error in HTTP Feed of {}, setting trust for uri {}", entity, uri);
                    throw Exceptions.propagate(e);
                }
            }

            Callable<HttpPollValue> pollJob;
            
            if (pollInfo.method.equals("get")) {
                pollJob = new Callable<HttpPollValue>() {
                    public HttpPollValue call() throws Exception {
                        if (log.isTraceEnabled()) log.trace("http polling for {} sensors at {}", entity, pollInfo);
                        return httpGet(httpClient, pollInfo.uriProvider.get(), pollInfo.headers);
                    }};
            } else if (pollInfo.method.equals("post")) {
                pollJob = new Callable<HttpPollValue>() {
                    public HttpPollValue call() throws Exception {
                        if (log.isTraceEnabled()) log.trace("http polling for {} sensors at {}", entity, pollInfo);
                        return httpPost(httpClient, pollInfo.uriProvider.get(), pollInfo.headers, pollInfo.body);
                    }};
            } else {
                throw new IllegalStateException("Unexpected http method: "+pollInfo.method);
            }
            
            getPoller().scheduleAtFixedRate(pollJob, new DelegatingPollHandler<HttpPollValue>(handlers), minPeriod);
        }
    }

    @SuppressWarnings("unchecked")
    private Poller<HttpPollValue> getPoller() {
        return (Poller<HttpPollValue>) poller;
    }
    
    private HttpPollValue httpGet(HttpClient httpClient, URI uri, Map<String,String> headers) throws ClientProtocolException, IOException {
        HttpGet httpGet = new HttpGet(uri);
        for (Map.Entry<String,String> entry : headers.entrySet()) {
            httpGet.addHeader(entry.getKey(), entry.getValue());
        }
        
        long startTime = System.currentTimeMillis();
        HttpResponse httpResponse = httpClient.execute(httpGet);
        try {
            return new HttpPollValue(httpResponse, startTime);
        } finally {
            EntityUtils.consume(httpResponse.getEntity());
        }
    }
    
    private HttpPollValue httpPost(HttpClient httpClient, URI uri, Map<String,String> headers, byte[] body) throws ClientProtocolException, IOException {
        HttpPost httpPost = new HttpPost(uri);
        for (Map.Entry<String,String> entry : headers.entrySet()) {
            httpPost.addHeader(entry.getKey(), entry.getValue());
        }
        if (body != null) {
            HttpEntity httpEntity = new ByteArrayEntity(body);
            httpPost.setEntity(httpEntity);
        }
        
        long startTime = System.currentTimeMillis();
        HttpResponse httpResponse = httpClient.execute(httpPost);
        
        try {
            return new HttpPollValue(httpResponse, startTime);
        } finally {
            EntityUtils.consume(httpResponse.getEntity());
        }
    }
    
    private static class TrustAllStrategy implements TrustStrategy {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }
    }
}
