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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;
import org.jboss.resteasy.specimpl.BuiltResponse;
import org.jboss.resteasy.util.GenericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import brooklyn.rest.api.AccessApi;
import brooklyn.rest.api.ActivityApi;
import brooklyn.rest.api.ApplicationApi;
import brooklyn.rest.api.CatalogApi;
import brooklyn.rest.api.EffectorApi;
import brooklyn.rest.api.EntityApi;
import brooklyn.rest.api.EntityConfigApi;
import brooklyn.rest.api.LocationApi;
import brooklyn.rest.api.PolicyApi;
import brooklyn.rest.api.PolicyConfigApi;
import brooklyn.rest.api.ScriptApi;
import brooklyn.rest.api.SensorApi;
import brooklyn.rest.api.ServerApi;
import brooklyn.rest.api.UsageApi;
import brooklyn.rest.api.VersionApi;
import brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.http.BuiltResponsePreservingError;

import com.wordnik.swagger.core.ApiOperation;

/**
 * @author Adam Lowe
 */
@SuppressWarnings("deprecation")
public class BrooklynApi {

    private final String target;
    private final ClientExecutor clientExecutor;
    private static final Logger LOG = LoggerFactory.getLogger(BrooklynApi.class);

    public BrooklynApi(URL endpoint) {
        this(checkNotNull(endpoint, "endpoint").toString());
    }

    public BrooklynApi(String endpoint) {
        // username/password cannot be null, but credentials can
        this(endpoint, null);
    }

    public BrooklynApi(URL endpoint, String username, String password) {
        this(endpoint.toString(), new UsernamePasswordCredentials(username, password));
    }

    public BrooklynApi(String endpoint, String username, String password) {
        this(endpoint, new UsernamePasswordCredentials(username, password));
    }

    public BrooklynApi(URL endpoint, @Nullable Credentials credentials) {
        this(endpoint.toString(), credentials);
    }

    public BrooklynApi(String endpoint, @Nullable Credentials credentials) {
        try {
            new URL(checkNotNull(endpoint, "endpoint"));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }

        this.target = endpoint;
        if (credentials != null) {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            httpClient.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
            this.clientExecutor = new ApacheHttpClient4Executor(httpClient);
        } else {
            this.clientExecutor = ClientRequest.getDefaultExecutor();
        }
    }

    public BrooklynApi(URL endpoint, ClientExecutor clientExecutor) {
        this.target = checkNotNull(endpoint, "endpoint").toString();
        this.clientExecutor = checkNotNull(clientExecutor, "clientExecutor");
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
     * @deprecated Use {@link #getEntity(Response, GenericType)} instead.
     */
    @Deprecated
    public static <T> T getEntityGeneric(Response response, GenericType<T> type) {
        return getEntity(response, type);
    }

}
