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

import javax.ws.rs.core.Response;

import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.util.GenericType;

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

/**
 * @author Adam Lowe
 */
@SuppressWarnings("deprecation")
public class BrooklynApi {

    private final String target;

    public BrooklynApi(String endpoint) {
        target = endpoint;
    }

    public ActivityApi getActivityApi() {
        return ProxyFactory.create(ActivityApi.class, target);
    }

    public ApplicationApi getApplicationApi() {
        return ProxyFactory.create(ApplicationApi.class, target);
    }

    public CatalogApi getCatalogApi() {
        return ProxyFactory.create(CatalogApi.class, target);
    }

    public EffectorApi getEffectorApi() {
        return ProxyFactory.create(EffectorApi.class, target);
    }

    public EntityConfigApi getEntityConfigApi() {
        return ProxyFactory.create(EntityConfigApi.class, target);
    }

    public EntityApi getEntityApi() {
        return ProxyFactory.create(EntityApi.class, target);
    }

    public LocationApi getLocationApi() {
        return ProxyFactory.create(LocationApi.class, target);
    }

    public PolicyConfigApi getPolicyConfigApi() {
        return ProxyFactory.create(PolicyConfigApi.class, target);
    }

    public PolicyApi getPolicyApi() {
        return ProxyFactory.create(PolicyApi.class, target);
    }

    public ScriptApi getScriptApi() {
        return ProxyFactory.create(ScriptApi.class, target);
    }

    public SensorApi getSensorApi() {
        return ProxyFactory.create(SensorApi.class, target);
    }

    public ServerApi getServerApi() {
        return ProxyFactory.create(ServerApi.class, target);
    }

    public UsageApi getUsageApi() {
        return ProxyFactory.create(UsageApi.class, target);
    }

    public VersionApi getVersionApi() {
        return ProxyFactory.create(VersionApi.class, target);
    }

    public AccessApi getAccessApi() {
        return ProxyFactory.create(AccessApi.class, target);
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
