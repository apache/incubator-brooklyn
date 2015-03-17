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
package brooklyn.rest.client;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;
import org.jboss.resteasy.util.GenericType;

import com.google.common.base.Charsets;

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
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;

/**
 * @author Adam Lowe
 */
@SuppressWarnings("deprecation")
public class BrooklynApi {

    private final String target;
    private final ClientExecutor clientExecutor;

    public BrooklynApi(URL endpoint) {
        this(checkNotNull(endpoint, "endpoint").toString());
    }

    public BrooklynApi(String endpoint) {
        this(endpoint, null, null);
    }

    public BrooklynApi(URL endpoint, String username, String password) {
        this(endpoint.toString(), new UsernamePasswordCredentials(username, password));
    }

    public BrooklynApi(String endpoint, String username, String password) {
        this(endpoint, new UsernamePasswordCredentials(username, password));
    }

    public BrooklynApi(URL endpoint, Credentials credentials) {
        this(endpoint.toString(), credentials);
    }

    public BrooklynApi(String endpoint, Credentials credentials) {
        URL target = null;
        try {
            target = new URL(checkNotNull(endpoint, "endpoint"));
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

    private <T> T proxy(Class<T> clazz) {
        return ProxyFactory.create(clazz, target, clientExecutor);
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

    @SuppressWarnings("unchecked")
    public static <T> T getEntity(Response response, Class<T> type) {
        if (!(response instanceof ClientResponse)) {
            throw new IllegalArgumentException("Response should be instance of ClientResponse, is: " + response.getClass());
        }
        ClientResponse clientResponse = (ClientResponse) response;
        return (T) clientResponse.getEntity(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getEntityGeneric(Response response, GenericType type) {
        if (!(response instanceof ClientResponse)) {
            throw new IllegalArgumentException("Response should be instance of ClientResponse, is: " + response.getClass());
        }
        ClientResponse clientResponse = (ClientResponse) response;
        return (T) clientResponse.getEntity(type);
    }

}
