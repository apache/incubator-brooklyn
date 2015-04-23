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
package brooklyn.rest.testing.mocks;

import java.net.URI;
import java.util.Collection;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.BrooklynCatalog;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.LocationRegistry;
import brooklyn.management.AccessController;
import brooklyn.management.EntityManager;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionManager;
import brooklyn.management.entitlement.EntitlementManager;
import brooklyn.management.ha.HighAvailabilityManager;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.util.guava.Maybe;

public class ManagementContextMock implements ManagementContext {
    private HighAvailabilityManagerStub haMock = new HighAvailabilityManagerStub();

    public void setState(ManagementNodeState state) {
        haMock.setState(state);
    }

    private static RuntimeException fail() {
        throw new UnsupportedOperationException("Mocked method not implemented");
    }

    @Override
    public HighAvailabilityManager getHighAvailabilityManager() {
        return haMock;
    }

    @Override
    public String getManagementPlaneId() {
        throw fail();
    }

    @Override
    public String getManagementNodeId() {
        throw fail();
    }

    @Override
    public Maybe<URI> getManagementNodeUri() {
        throw fail();
    }

    @Override
    public Collection<Application> getApplications() {
        throw fail();
    }

    @Override
    public EntityManager getEntityManager() {
        throw fail();
    }

    @Override
    public ExecutionManager getExecutionManager() {
        throw fail();
    }

    @Override
    public ExecutionContext getServerExecutionContext() {
        throw fail();
    }

    @Override
    public EntityDriverManager getEntityDriverManager() {
        throw fail();
    }

    @Override
    public DownloadResolverManager getEntityDownloadsManager() {
        throw fail();
    }

    @Override
    public SubscriptionManager getSubscriptionManager() {
        throw fail();
    }

    @Override
    public ExecutionContext getExecutionContext(Entity entity) {
        throw fail();
    }

    @Override
    public SubscriptionContext getSubscriptionContext(Entity entity) {
        throw fail();
    }

    @Override
    public RebindManager getRebindManager() {
        throw fail();
    }

    @Override
    public StringConfigMap getConfig() {
        throw fail();
    }

    @Override
    public boolean isRunning() {
        throw fail();
    }

    @Override
    public boolean isStartupComplete() {
        throw fail();
    }
    
    @Override
    public LocationRegistry getLocationRegistry() {
        throw fail();
    }

    @Override
    public BrooklynCatalog getCatalog() {
        throw fail();
    }

    @Override
    public LocationManager getLocationManager() {
        throw fail();
    }

    @Override
    public AccessController getAccessController() {
        throw fail();
    }

    @Override
    public void reloadBrooklynProperties() {
        throw fail();

    }

    @Override
    public void addPropertiesReloadListener(PropertiesReloadListener listener) {
        throw fail();

    }

    @Override
    public void removePropertiesReloadListener(PropertiesReloadListener listener) {
        throw fail();
    }

    @Override
    public EntitlementManager getEntitlementManager() {
        throw fail();
    }

    @Override
    public BrooklynObject lookup(String id) {
        throw fail();
    }

    @Override
    public <T extends BrooklynObject> T lookup(String id, Class<T> type) {
        throw fail();
    }

}
