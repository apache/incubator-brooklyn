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
package brooklyn.util.http;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

import com.google.common.base.Optional;
import com.google.common.collect.Multimap;

public class HttpTool {

    private static final Logger LOG = LoggerFactory.getLogger(HttpTool.class);

    public static class TrustAllStrategy implements TrustStrategy {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }
    }

    public static HttpClientBuilder httpClientBuilder() {
        return new HttpClientBuilder();
    }
    
    public static class HttpClientBuilder {
        private ClientConnectionManager clientConnectionManager;
        private HttpParams httpParams;
        private URI uri;
        private Integer port;
        private Credentials credentials;
        private boolean laxRedirect;
        private Boolean https;
        private SchemeSocketFactory socketFactory;
        private ConnectionReuseStrategy reuseStrategy;
        private boolean trustAll;
        private boolean trustSelfSigned;

        public HttpClientBuilder clientConnectionManager(ClientConnectionManager val) {
            this.clientConnectionManager = checkNotNull(val, "clientConnectionManager");
            return this;
        }
        public HttpClientBuilder httpParams(HttpParams val) {
            checkState(httpParams == null, "Must not call httpParams multiple times, or after other methods like connectionTimeout");
            this.httpParams = checkNotNull(val, "httpParams");
            return this;
        }
        public HttpClientBuilder connectionTimeout(Duration val) {
            if (httpParams == null) httpParams = new BasicHttpParams();
            long millis = checkNotNull(val, "connectionTimeout").toMilliseconds();
            if (millis > Integer.MAX_VALUE) throw new IllegalStateException("HttpClient only accepts upto max-int millis for connectionTimeout, but given "+val);
            HttpConnectionParams.setConnectionTimeout(httpParams, (int) millis);
            return this;
        }
        public HttpClientBuilder socketTimeout(Duration val) {
            if (httpParams == null) httpParams = new BasicHttpParams();
            long millis = checkNotNull(val, "socketTimeout").toMilliseconds();
            if (millis > Integer.MAX_VALUE) throw new IllegalStateException("HttpClient only accepts upto max-int millis for socketTimeout, but given "+val);
            HttpConnectionParams.setSoTimeout(httpParams, (int) millis);
            return this;
        }
        public HttpClientBuilder reuseStrategy(ConnectionReuseStrategy val) {
            this.reuseStrategy = checkNotNull(val, "reuseStrategy");
            return this;
        }
        public HttpClientBuilder uri(String val) {
            return uri(URI.create(checkNotNull(val, "uri")));
        }
        public HttpClientBuilder uri(URI val) {
            this.uri = checkNotNull(val, "uri");
            if (https == null) https = ("https".equalsIgnoreCase(uri.getScheme()));
            return this;
        }
        public HttpClientBuilder port(int val) {
            this.port = val;
            return this;
        }
        public HttpClientBuilder credentials(Credentials val) {
            this.credentials = checkNotNull(val, "credentials");
            return this;
        }
        public void credential(Optional<Credentials> val) {
            if (val.isPresent()) credentials = val.get();
        }
        /** similar to curl --post301 -L` */
        public HttpClientBuilder laxRedirect(boolean val) {
            this.laxRedirect = val;
            return this;
        }
        public HttpClientBuilder https(boolean val) {
            this.https = val;
            return this;
        }
        public HttpClientBuilder socketFactory(SchemeSocketFactory val) {
            this.socketFactory = checkNotNull(val, "socketFactory");
            return this;
        }
        public HttpClientBuilder trustAll() {
            this.trustAll = true;
            return this;
        }
        public HttpClientBuilder trustSelfSigned() {
            this.trustSelfSigned = true;
            return this;
        }
        public HttpClient build() {
            final DefaultHttpClient httpClient = new DefaultHttpClient(clientConnectionManager);
            httpClient.setParams(httpParams);
    
            // support redirects for POST (similar to `curl --post301 -L`)
            // http://stackoverflow.com/questions/3658721/httpclient-4-error-302-how-to-redirect
            if (laxRedirect) {
                httpClient.setRedirectStrategy(new LaxRedirectStrategy());
            }
            if (reuseStrategy != null) {
                httpClient.setReuseStrategy(reuseStrategy);
            }
            if (https == Boolean.TRUE || (uri!=null && uri.toString().startsWith("https:"))) {
                try {
                    if (port == null) {
                        port = (uri != null && uri.getPort() >= 0) ? uri.getPort() : 443;
                    }
                    if (socketFactory == null) {
                        if (trustAll) {
                            TrustStrategy trustStrategy = new TrustAllStrategy();
                            X509HostnameVerifier hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
                            socketFactory = new SSLSocketFactory(trustStrategy, hostnameVerifier);
                        } else if (trustSelfSigned) {
                            TrustStrategy trustStrategy = new TrustSelfSignedStrategy();
                            X509HostnameVerifier hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
                            socketFactory = new SSLSocketFactory(trustStrategy, hostnameVerifier);
                        } else {
                            // Using default https scheme: based on default java truststore, which is pretty strict!
                        }
                    }
                    if (socketFactory != null) {
                        Scheme sch = new Scheme("https", port, socketFactory);
                        httpClient.getConnectionManager().getSchemeRegistry().register(sch);
                    }
                } catch (Exception e) {
                    LOG.warn("Error setting trust for uri {}", uri);
                    throw Exceptions.propagate(e);
                }
            }
    
            // Set credentials
            if (uri != null && credentials != null) {
                String hostname = uri.getHost();
                int port = uri.getPort();
                httpClient.getCredentialsProvider().setCredentials(new AuthScope(hostname, port), credentials);
            }
            if (uri==null && credentials!=null) {
                LOG.warn("credentials have no effect in builder unless URI for host is specified");
            }
    
            return httpClient;
        }
    }

    protected static abstract class HttpRequestBuilder<B extends HttpRequestBuilder<B, R>, R extends HttpRequest> {
        protected R req;
        
        protected HttpRequestBuilder(R req) {
            this.req = req;
        }
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
        public B headers(Map<String,String> headers) {
            if (headers!=null) {
                for (Map.Entry<String,String> entry : headers.entrySet()) {
                    req.addHeader(entry.getKey(), entry.getValue());
                }
            }
            return self();
        }
        public B headers(Multimap<String,String> headers) {
            if (headers!=null) {
                for (Map.Entry<String,String> entry : headers.entries()) {
                    req.addHeader(entry.getKey(), entry.getValue());
                }
            }
            return self();
        }
        public R build() {
            return req;
        }
    }
    
    protected static abstract class HttpEntityEnclosingRequestBaseBuilder<B extends HttpEntityEnclosingRequestBaseBuilder<B,R>, R extends HttpEntityEnclosingRequestBase> extends HttpRequestBuilder<B, R> {
        protected HttpEntityEnclosingRequestBaseBuilder(R req) {
            super(req);
        }
        public B body(byte[] body) {
            if (body != null) {
                HttpEntity httpEntity = new ByteArrayEntity(body);
                req.setEntity(httpEntity);
            }
            return self();
        }
    }
    
    public static class HttpGetBuilder extends HttpRequestBuilder<HttpGetBuilder, HttpGet> {
        public HttpGetBuilder(URI uri) {
            super(new HttpGet(uri));
        }
    }
    
    public static class HttpHeadBuilder extends HttpRequestBuilder<HttpHeadBuilder, HttpHead> {
        public HttpHeadBuilder(URI uri) {
            super(new HttpHead(uri));
        }
    }
    
    public static class HttpDeleteBuilder extends HttpRequestBuilder<HttpDeleteBuilder, HttpDelete> {
        public HttpDeleteBuilder(URI uri) {
            super(new HttpDelete(uri));
        }
    }
    
    public static class HttpPostBuilder extends HttpEntityEnclosingRequestBaseBuilder<HttpPostBuilder, HttpPost> {
        HttpPostBuilder(URI uri) {
            super(new HttpPost(uri));
        }
    }
    
    public static class HttpPutBuilder extends HttpEntityEnclosingRequestBaseBuilder<HttpPutBuilder, HttpPut> {
        public HttpPutBuilder(URI uri) {
            super(new HttpPut(uri));
        }
    }
    
    public static HttpToolResponse httpGet(HttpClient httpClient, URI uri, Map<String,String> headers) {
        HttpGet req = new HttpGetBuilder(uri).headers(headers).build();
        return execAndConsume(httpClient, req);
    }
    
    public static HttpToolResponse httpPost(HttpClient httpClient, URI uri, Map<String,String> headers, byte[] body) {
        HttpPost req = new HttpPostBuilder(uri).headers(headers).body(body).build();
        return execAndConsume(httpClient, req);
    }

    public static HttpToolResponse httpPut(HttpClient httpClient, URI uri, Map<String, String> headers, byte[] body) {
        HttpPut req = new HttpPutBuilder(uri).headers(headers).body(body).build();
        return execAndConsume(httpClient, req);
    }

    public static HttpToolResponse httpDelete(HttpClient httpClient, URI uri, Map<String,String> headers) {
        HttpDelete req = new HttpDeleteBuilder(uri).headers(headers).build();
        return execAndConsume(httpClient, req);
    }
    
    public static HttpToolResponse httpHead(HttpClient httpClient, URI uri, Map<String,String> headers) {
        HttpHead req = new HttpHeadBuilder(uri).headers(headers).build();
        return execAndConsume(httpClient, req);
    }
    
    public static HttpToolResponse execAndConsume(HttpClient httpClient, HttpUriRequest req) {
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse httpResponse = httpClient.execute(req);
            
            try {
                return new HttpToolResponse(httpResponse, startTime);
            } finally {
                EntityUtils.consume(httpResponse.getEntity());
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public static boolean isStatusCodeHealthy(int code) { return (code>=200 && code<=299); }

    public static String toBasicAuthorizationValue(UsernamePasswordCredentials credentials) {
        return "Basic "+Base64.encodeBase64String( (credentials.getUserName()+":"+credentials.getPassword()).getBytes() );
    }

}
