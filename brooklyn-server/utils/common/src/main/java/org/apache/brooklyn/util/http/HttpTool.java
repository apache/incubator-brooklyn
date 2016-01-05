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
package org.apache.brooklyn.util.http;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.base.Throwables;
import org.apache.brooklyn.util.crypto.SslTrustUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.URLParamEncoder;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * A utility tool for HTTP operations.
 */
public class HttpTool {

    private static final Logger LOG = LoggerFactory.getLogger(HttpTool.class);

    static final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Connects to the given url and returns the connection.
     * Caller should {@code connection.getInputStream().close()} the result of this
     * (especially if they are making heavy use of this method).
     */
    public static URLConnection connectToUrl(String u) throws Exception {
        final URL url = new URL(u);
        final AtomicReference<Exception> exception = new AtomicReference<Exception>();

        // sometimes openConnection hangs, so run in background
        Future<URLConnection> f = executor.submit(new Callable<URLConnection>() {
            public URLConnection call() {
                try {
                    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String s, SSLSession sslSession) {
                            return true;
                        }
                    });
                    URLConnection connection = url.openConnection();
                    TrustingSslSocketFactory.configure(connection);
                    connection.connect();

                    connection.getContentLength(); // Make sure the connection is made.
                    return connection;
                } catch (Exception e) {
                    exception.set(e);
                    LOG.debug("Error connecting to url "+url+" (propagating): "+e, e);
                }
                return null;
            }
        });
        try {
            URLConnection result = null;
            try {
                result = f.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                LOG.debug("Error connecting to url "+url+", probably timed out (rethrowing): "+e);
                throw new IllegalStateException("Connect to URL not complete within 60 seconds, for url "+url+": "+e);
            }
            if (exception.get() != null) {
                LOG.debug("Error connecting to url "+url+", thread caller of "+exception, new Throwable("source of rethrown error "+exception));
                throw exception.get();
            } else {
                return result;
            }
        } finally {
            f.cancel(true);
        }
    }



    public static int getHttpStatusCode(String url) throws Exception {
        URLConnection connection = connectToUrl(url);
        long startTime = System.currentTimeMillis();
        int status = ((HttpURLConnection) connection).getResponseCode();

        // read fully if possible, then close everything, trying to prevent cached threads at server
        consumeAndCloseQuietly((HttpURLConnection) connection);

        if (LOG.isDebugEnabled())
            LOG.debug("connection to {} ({}ms) gives {}", new Object[] { url, (System.currentTimeMillis()-startTime), status });
        return status;
    }


    public static String getContent(String url) {
        try {
            return Streams.readFullyString(SslTrustUtils.trustAll(new URL(url).openConnection()).getInputStream());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static String getErrorContent(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) connectToUrl(url);
            long startTime = System.currentTimeMillis();

            String err;
            int status;
            try {
                InputStream errStream = connection.getErrorStream();
                err = Streams.readFullyString(errStream);
                status = connection.getResponseCode();
            } finally {
                closeQuietly(connection);
            }

            if (LOG.isDebugEnabled())
                LOG.debug("read of err {} ({}ms) complete; http code {}", new Object[] { url, Time.makeTimeStringRounded(System.currentTimeMillis() - startTime), status});
            return err;

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    /**
     * Consumes the input stream entirely and then cleanly closes the connection.
     * Ignores all exceptions completely, not even logging them!
     *
     * Consuming the stream fully is useful for preventing idle TCP connections.
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html">Persistent Connections</a>
     */
    public static void consumeAndCloseQuietly(HttpURLConnection connection) {
        try { Streams.readFully(connection.getInputStream()); } catch (Exception e) {}
        closeQuietly(connection);
    }

    /**
     * Closes all streams of the connection, and disconnects it. Ignores all exceptions completely,
     * not even logging them!
     */
    public static void closeQuietly(HttpURLConnection connection) {
        try { connection.disconnect(); } catch (Exception e) {}
        try { connection.getInputStream().close(); } catch (Exception e) {}
        try { connection.getOutputStream().close(); } catch (Exception e) {}
        try { connection.getErrorStream().close(); } catch (Exception e) {}
    }

    /** Apache HTTP commons utility for trusting all.
     * <p>
     * For generic java HTTP usage, see {@link SslTrustUtils#trustAll(java.net.URLConnection)} 
     * and static constants in the same class. */
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

    public static class HttpFormPostBuilder extends HttpRequestBuilder<HttpFormPostBuilder, HttpPost> {
        HttpFormPostBuilder(URI uri) {
            super(new HttpPost(uri));
        }

        public HttpFormPostBuilder params(Map<String, String> params) {
            if (params != null) {
                Collection<NameValuePair> httpParams = new ArrayList<NameValuePair>(params.size());
                for (Entry<String, String> param : params.entrySet()) {
                    httpParams.add(new BasicNameValuePair(param.getKey(), param.getValue()));
                }
                req.setEntity(new UrlEncodedFormEntity(httpParams));
            }
            return self();
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

    public static HttpToolResponse httpPost(HttpClient httpClient, URI uri, Map<String,String> headers, Map<String, String> params) {
        HttpPost req = new HttpFormPostBuilder(uri).headers(headers).params(params).build();
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

    public static String encodeUrlParams(Map<?,?> data) {
        if (data==null) return "";
        Iterable<String> args = Iterables.transform(data.entrySet(), 
            new Function<Map.Entry<?,?>,String>() {
            @Override public String apply(Map.Entry<?,?> entry) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                return URLParamEncoder.encode(Strings.toString(k)) + (v != null ? "=" + URLParamEncoder.encode(Strings.toString(v)) : "");
            }
        });
        return Joiner.on("&").join(args);
    }
}
