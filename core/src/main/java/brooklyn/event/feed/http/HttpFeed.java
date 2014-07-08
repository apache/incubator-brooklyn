/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.event.feed.http;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpTool.HttpClientBuilder;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
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
 * (e.g. {@link Entities#attributeSupplier(brooklyn.entity.Entity, brooklyn.event.AttributeSensor)})
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
        private boolean onlyIfServiceUp = false;
        private Supplier<URI> baseUriProvider;
        private Duration period = Duration.millis(500);
        private List<HttpPollConfig<?>> polls = Lists.newArrayList();
        private URI baseUri;
        private Map<String, String> baseUriVars = Maps.newLinkedHashMap();
        private Map<String, String> headers = Maps.newLinkedHashMap();
        private boolean suspended = false;
        private Credentials credentials;
        private volatile boolean built;

        public Builder entity(EntityLocal val) {
            this.entity = val;
            return this;
        }
        public Builder onlyIfServiceUp() { return onlyIfServiceUp(true); }
        public Builder onlyIfServiceUp(boolean onlyIfServiceUp) { 
            this.onlyIfServiceUp = onlyIfServiceUp; 
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
        public Builder period(Duration duration) {
            this.period = duration;
            return this;
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            return period(Duration.of(val, units));
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
        public Builder credentials(String username, String password) {
            this.credentials = new UsernamePasswordCredentials(username, password);
            return this;
        }
        public Builder credentialsIfNotNull(String username, String password) {
            if (username != null) {
                this.credentials = new UsernamePasswordCredentials(username, password);
            }
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
        final Optional<Credentials> credentials;
        final Duration connectionTimeout;
        final Duration socketTimeout;
        private HttpPollIdentifier(String method, Supplier<URI> uriProvider, Map<String, String> headers, byte[] body,
                                   Optional<Credentials> credentials, Duration connectionTimeout, Duration socketTimeout) {
            this.method = checkNotNull(method, "method").toLowerCase();
            this.uriProvider = checkNotNull(uriProvider, "uriProvider");
            this.headers = checkNotNull(headers, "headers");
            this.body = body;
            this.credentials = checkNotNull(credentials, "credentials");
            this.connectionTimeout = connectionTimeout;
            this.socketTimeout = socketTimeout;
            
            if (!(this.method.equals("get") || this.method.equals("post"))) {
                throw new IllegalArgumentException("Unsupported HTTP method (only supports GET and POST): "+method);
            }
            if (body != null && method.equalsIgnoreCase("get")) {
                throw new IllegalArgumentException("Must not set body for http GET method");
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(method, uriProvider, headers, body, credentials);
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
                    Objects.equal(body, o.body) &&
                    Objects.equal(credentials, o.credentials);
        }
    }
    
    // Treat as immutable once built
    private final SetMultimap<HttpPollIdentifier, HttpPollConfig<?>> polls = HashMultimap.<HttpPollIdentifier,HttpPollConfig<?>>create();
    
    protected HttpFeed(Builder builder) {
        super(builder.entity, builder.onlyIfServiceUp);
        Map<String,String> baseHeaders = ImmutableMap.copyOf(checkNotNull(builder.headers, "headers"));
        
        for (HttpPollConfig<?> config : builder.polls) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            HttpPollConfig<?> configCopy = new HttpPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period);
            String method = config.getMethod();
            Map<String,String> headers = config.buildHeaders(baseHeaders);
            byte[] body = config.getBody();
            Duration connectionTimeout = config.getConnectionTimeout();
            Duration socketTimeout = config.getSocketTimeout();
            
            Optional<Credentials> credentials = Optional.fromNullable(builder.credentials);
            
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

            polls.put(new HttpPollIdentifier(method, baseUriProvider, headers, body, credentials, connectionTimeout, socketTimeout), configCopy);
        }
    }

    @Override
    protected void preStart() {
        for (final HttpPollIdentifier pollInfo : polls.keySet()) {
            // Though HttpClients are thread safe and can take advantage of connection pooling
            // and authentication caching, the httpcomponents documentation says:
            //    "While HttpClient instances are thread safe and can be shared between multiple
            //     threads of execution, it is highly recommended that each thread maintains its
            //     own dedicated instance of HttpContext.
            //  http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html
            final HttpClient httpClient = createHttpClient(pollInfo);

            Set<HttpPollConfig<?>> configs = polls.get(pollInfo);
            long minPeriod = Integer.MAX_VALUE;
            Set<AttributePollHandler<? super HttpToolResponse>> handlers = Sets.newLinkedHashSet();

            for (HttpPollConfig<?> config : configs) {
                handlers.add(new AttributePollHandler<HttpToolResponse>(config, entity, this));
                if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
            }

            Callable<HttpToolResponse> pollJob;
            
            if (pollInfo.method.equals("get")) {
                pollJob = new Callable<HttpToolResponse>() {
                    public HttpToolResponse call() throws Exception {
                        if (log.isTraceEnabled()) log.trace("http polling for {} sensors at {}", entity, pollInfo);
                        return HttpTool.httpGet(httpClient, pollInfo.uriProvider.get(), pollInfo.headers);
                    }};
            } else if (pollInfo.method.equals("post")) {
                pollJob = new Callable<HttpToolResponse>() {
                    public HttpToolResponse call() throws Exception {
                        if (log.isTraceEnabled()) log.trace("http polling for {} sensors at {}", entity, pollInfo);
                        return HttpTool.httpPost(httpClient, pollInfo.uriProvider.get(), pollInfo.headers, pollInfo.body);
                    }};
            } else if (pollInfo.method.equals("head")) {
                pollJob = new Callable<HttpToolResponse>() {
                    public HttpToolResponse call() throws Exception {
                        if (log.isTraceEnabled()) log.trace("http polling for {} sensors at {}", entity, pollInfo);
                        return HttpTool.httpHead(httpClient, pollInfo.uriProvider.get(), pollInfo.headers);
                    }};
            } else {
                throw new IllegalStateException("Unexpected http method: "+pollInfo.method);
            }
            
            getPoller().scheduleAtFixedRate(pollJob, new DelegatingPollHandler<HttpToolResponse>(handlers), minPeriod);
        }
    }

    // TODO Should we really trustAll for https? Make configurable?
    private HttpClient createHttpClient(HttpPollIdentifier pollIdentifier) {
        URI uri = pollIdentifier.uriProvider.get();
        HttpClientBuilder builder = HttpTool.httpClientBuilder()
                .trustAll()
                .laxRedirect(true);
        if (uri != null) builder.uri(uri);
        if (uri != null) builder.credential(pollIdentifier.credentials);
        if (pollIdentifier.connectionTimeout != null) {
            builder.connectionTimeout(pollIdentifier.connectionTimeout);
        }
        if (pollIdentifier.socketTimeout != null) {
            builder.socketTimeout(pollIdentifier.socketTimeout);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Poller<HttpToolResponse> getPoller() {
        return (Poller<HttpToolResponse>) poller;
    }
}
