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
package org.apache.brooklyn.rest.client;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;
import org.jboss.resteasy.specimpl.BuiltResponse;
import org.jboss.resteasy.util.GenericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import org.apache.brooklyn.rest.api.AccessApi;
import org.apache.brooklyn.rest.api.ActivityApi;
import org.apache.brooklyn.rest.api.ApplicationApi;
import org.apache.brooklyn.rest.api.CatalogApi;
import org.apache.brooklyn.rest.api.EffectorApi;
import org.apache.brooklyn.rest.api.EntityApi;
import org.apache.brooklyn.rest.api.EntityConfigApi;
import org.apache.brooklyn.rest.api.LocationApi;
import org.apache.brooklyn.rest.api.PolicyApi;
import org.apache.brooklyn.rest.api.PolicyConfigApi;
import org.apache.brooklyn.rest.api.ScriptApi;
import org.apache.brooklyn.rest.api.SensorApi;
import org.apache.brooklyn.rest.api.ServerApi;
import org.apache.brooklyn.rest.api.UsageApi;
import org.apache.brooklyn.rest.api.VersionApi;
import org.apache.brooklyn.rest.client.util.http.BuiltResponsePreservingError;
import org.apache.brooklyn.util.exceptions.Exceptions;

import com.wordnik.swagger.core.ApiOperation;

/**
 * @author Adam Lowe
     */
@SuppressWarnings("deprecation")
public class BrooklynApi {

    private final String target;
    private final ClientExecutor clientExecutor;
    private final int maxPoolSize;
    private final int timeOutInMillis;
    private static final Logger LOG = LoggerFactory.getLogger(BrooklynApi.class);

    /**
     * @deprecated since 0.9.0. Use {@link BrooklynApi#newInstance(String)} instead
     */
    @Deprecated
    public BrooklynApi(URL endpoint) {
        this(checkNotNull(endpoint, "endpoint").toString());
    }

    /**
     * @deprecated since 0.9.0. Use {@link BrooklynApi#newInstance(String)} instead
     */
    @Deprecated
    public BrooklynApi(String endpoint) {
        // username/password cannot be null, but credentials can
        this(endpoint, null);
    }

    /**
     * @deprecated since 0.9.0. Use {@link BrooklynApi#newInstance(String, String, String)} instead
     */
    @Deprecated
    public BrooklynApi(URL endpoint, String username, String password) {
        this(endpoint.toString(), new UsernamePasswordCredentials(username, password));
    }

    /**
     * @deprecated since 0.9.0. Use {@link BrooklynApi#newInstance(String, String, String)} instead
     */
    @Deprecated
    public BrooklynApi(String endpoint, String username, String password) {
        this(endpoint, new UsernamePasswordCredentials(username, password));
    }

    /**
     * @deprecated since 0.9.0. Use {@link BrooklynApi#newInstance(String, String, String)} instead
     */
    @Deprecated
    public BrooklynApi(URL endpoint, @Nullable Credentials credentials) {
        this(endpoint.toString(), credentials);
    }

    /**
     * Creates a BrooklynApi using an HTTP connection pool
     *
     * @deprecated since 0.9.0. Use {@link BrooklynApi#newInstance(String, String, String)} instead
     */
    @Deprecated
    public BrooklynApi(String endpoint, @Nullable Credentials credentials) {
        this(endpoint, credentials, 20, 5000);
    }

    /**
     * Creates a BrooklynApi using a custom ClientExecutor
     *
     * @param endpoint the Brooklyn endpoint
     * @param clientExecutor the ClientExecutor
     * @see #getClientExecutor(org.apache.http.auth.Credentials)
     */
    public BrooklynApi(URL endpoint, ClientExecutor clientExecutor) {
        this.target = checkNotNull(endpoint, "endpoint").toString();
        this.maxPoolSize = -1;
        this.timeOutInMillis = -1;
        this.clientExecutor = checkNotNull(clientExecutor, "clientExecutor");
    }

    /**
     * Creates a BrooklynApi using an HTTP connection pool
     *
     * @param endpoint the Brooklyn endpoint
     * @param credentials user credentials or null
     * @param maxPoolSize maximum pool size
     * @param timeOutInMillis connection timeout in milliseconds
     */
    public BrooklynApi(String endpoint, @Nullable Credentials credentials, int maxPoolSize, int timeOutInMillis) {
        try {
            new URL(checkNotNull(endpoint, "endpoint"));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
        this.target = endpoint;
        this.maxPoolSize = maxPoolSize;
        this.timeOutInMillis = timeOutInMillis;
        this.clientExecutor = getClientExecutor(credentials);
    }

    private Supplier<PoolingHttpClientConnectionManager> connectionManagerSupplier = Suppliers.memoize(new Supplier<PoolingHttpClientConnectionManager>() {
        @Override
        public PoolingHttpClientConnectionManager get() {
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(maxPoolSize);
            connManager.setDefaultMaxPerRoute(maxPoolSize);
            return connManager;
        }
    });

    private Supplier<RequestConfig> reqConfSupplier = Suppliers.memoize(new Supplier<RequestConfig>() {
        @Override
        public RequestConfig get() {
            return RequestConfig.custom()
                    .setConnectTimeout(timeOutInMillis)
                    .setConnectionRequestTimeout(timeOutInMillis)
                    .build();
        }
    });

    /**
     * Creates a ClientExecutor for this BrooklynApi
     */
    protected ClientExecutor getClientExecutor(Credentials credentials) {
        CredentialsProvider provider = new BasicCredentialsProvider();
        if (credentials != null) provider.setCredentials(AuthScope.ANY, credentials);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(provider)
                .setDefaultRequestConfig(reqConfSupplier.get())
                .setConnectionManager(connectionManagerSupplier.get())
                .build();

        return new ApacheHttpClient4Executor(httpClient);
    }

    /**
     * Creates a BrooklynApi using an HTTP connection pool
     *
     * @param endpoint the Brooklyn endpoint
     * @return a new BrooklynApi instance
     */
    public static BrooklynApi newInstance(String endpoint) {
        return new BrooklynApi(endpoint, null);
    }

    /**
     * Creates a BrooklynApi using an HTTP connection pool
     *
     * @param endpoint the Brooklyn endpoint
     * @param maxPoolSize maximum connection pool size
     * @param timeOutInMillis connection timeout in millisecond
     * @return a new BrooklynApi instance
     */
    public static BrooklynApi newInstance(String endpoint, int maxPoolSize, int timeOutInMillis) {
        return new BrooklynApi(endpoint, null, maxPoolSize, timeOutInMillis);
    }

    /**
     * Creates a BrooklynApi using an HTTP connection pool
     *
     * @param endpoint the Brooklyn endpoint
     * @param username for authentication
     * @param password for authentication
     * @return a new BrooklynApi instance
     */
    public static BrooklynApi newInstance(String endpoint, String username, String password) {
        return new BrooklynApi(endpoint, new UsernamePasswordCredentials(username, password));
    }

    /**
     * Creates a BrooklynApi using an HTTP connection pool
     *
     * @param endpoint the Brooklyn endpoint
     * @param username for authentication
     * @param password for authentication
     * @param maxPoolSize maximum connection pool size
     * @param timeOutInMillis connection timeout in millisecond
     * @return a new BrooklynApi instance
     */
    public static BrooklynApi newInstance(String endpoint, String username, String password, int maxPoolSize, int timeOutInMillis) {
        return new BrooklynApi(endpoint, new UsernamePasswordCredentials(username, password), maxPoolSize, timeOutInMillis);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> clazz) {
        final T result0 = ProxyFactory.create(clazz, target, clientExecutor);
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                try {
                    Object result1 = method.invoke(result0, args);
                    Class<?> type = String.class;
                    if (result1 instanceof Response) {
                        Response resp = (Response)result1;
                        if(isStatusCodeHealthy(resp.getStatus()) && method.isAnnotationPresent(ApiOperation.class)) {
                           type = getClassFromMethodAnnotationOrDefault(method, type);
                        }
                        // wrap the original response so it self-closes
                        result1 = BuiltResponsePreservingError.copyResponseAndClose(resp, type);
                    }
                    return result1;
                } catch (Throwable e) {
                    if (e instanceof InvocationTargetException) {
                        // throw the original exception
                        e = ((InvocationTargetException)e).getTargetException();
                    }
                    throw Exceptions.propagate(e);
                }
            }

            private boolean isStatusCodeHealthy(int code) { return (code>=200 && code<=299); }

            private Class<?> getClassFromMethodAnnotationOrDefault(Method method, Class<?> def){
                Class<?> type;
                try{
                    String responseClass = method.getAnnotation(ApiOperation.class).responseClass();
                    type = Class.forName(responseClass);
                } catch (Exception e) {
                    type = def;
                    LOG.debug("Unable to get class from annotation: {}.  Defaulting to {}", e.getMessage(), def.getName());
                    Exceptions.propagateIfFatal(e);
                }
                return type;
            }
        });
    }

    public ActivityApi getActivityApi() {
        return proxy(ActivityApi.class);
    }

    public ApplicationApi getApplicationApi() {
        return proxy(ApplicationApi.class);
    }

    public CatalogApi getCatalogApi() {
        return proxy(CatalogApi.class);
    }

    public EffectorApi getEffectorApi() {
        return proxy(EffectorApi.class);
    }

    public EntityConfigApi getEntityConfigApi() {
        return proxy(EntityConfigApi.class);
    }

    public EntityApi getEntityApi() {
        return proxy(EntityApi.class);
    }

    public LocationApi getLocationApi() {
        return proxy(LocationApi.class);
    }

    public PolicyConfigApi getPolicyConfigApi() {
        return proxy(PolicyConfigApi.class);
    }

    public PolicyApi getPolicyApi() {
        return proxy(PolicyApi.class);
    }

    public ScriptApi getScriptApi() {
        return proxy(ScriptApi.class);
    }

    public SensorApi getSensorApi() {
        return proxy(SensorApi.class);
    }

    public ServerApi getServerApi() {
        return proxy(ServerApi.class);
    }

    public UsageApi getUsageApi() {
        return proxy(UsageApi.class);
    }

    public VersionApi getVersionApi() {
        return proxy(VersionApi.class);
    }

    public AccessApi getAccessApi() {
        return proxy(AccessApi.class);
    }

    public static <T> T getEntity(Response response, Class<T> type) {
        if (response instanceof ClientResponse) {
            ClientResponse<?> clientResponse = (ClientResponse<?>) response;
            return clientResponse.getEntity(type);
        } else if (response instanceof BuiltResponse) {
            // Handle BuiltResponsePreservingError turning objects into Strings
            if (response.getEntity() instanceof String && !type.equals(String.class)) {
                return new Gson().fromJson(response.getEntity().toString(), type);
            }
        }
        // Last-gasp attempt.
        return type.cast(response.getEntity());
    }

    public static <T> T getEntity(Response response, GenericType<T> type) {
        if (response instanceof ClientResponse) {
            ClientResponse<?> clientResponse = (ClientResponse<?>) response;
            return clientResponse.getEntity(type);
        } else if (response instanceof BuiltResponse) {
            // Handle BuiltResponsePreservingError turning objects into Strings
            if (response.getEntity() instanceof String) {
                return new Gson().fromJson(response.getEntity().toString(), type.getGenericType());
            }
        }
        // Last-gasp attempt.
        return type.getType().cast(response.getEntity());
    }

    /**
     * @deprecated since 0.9.0. Use {@link #getEntity(Response, GenericType)} instead.
     */
    @Deprecated
    public static <T> T getEntityGeneric(Response response, GenericType<T> type) {
        return getEntity(response, type);
    }

}
