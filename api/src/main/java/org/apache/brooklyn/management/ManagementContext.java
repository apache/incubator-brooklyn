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
package org.apache.brooklyn.management;

import java.io.Serializable;
import java.net.URI;
import java.util.Collection;

import brooklyn.basic.BrooklynObject;

import org.apache.brooklyn.catalog.BrooklynCatalog;
import org.apache.brooklyn.management.entitlement.EntitlementManager;
import org.apache.brooklyn.management.ha.HighAvailabilityManager;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.LocationRegistry;
import brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;

/**
 * This is the entry point for accessing and interacting with a realm of applications and their entities in Brooklyn.
 *
 * For example, policies and the management console(s) (web-app, etc) can use this to interact with entities; 
 * policies, web-app, and entities share the realm for subscribing to events, executing tasks, and generally co-existing.      
 * <p>
 * It may refer to several applications, and it refers to all the entities descended from those applications.
 */
public interface ManagementContext {

    // TODO Consider separating out into a ConfigManager for methods like:
    //  - getConfig()
    //  - reloadBrooklynProperties();
    //  - addPropertiesReloadListener
    //  - removePropertiesReloadListener
    //  - interface PropertiesReloadListener
    
    /** 
     * UID for the Brooklyn management plane which this {@link ManagementContext} node is a part of.
     * <p>
     * Each Brooklyn entity is actively managed by a unique management plane 
     * whose ID which should not normally change for the duration of that entity, 
     * even though the nodes in that plane might, and the plane may go down and come back up. 
     * In other words the value of {@link Application#getManagementContext()#getManagementPlaneId()} 
     * will generally be constant (in contrast to {@link #getManagementNodeId()}).
     * <p>
     * This value should not be null unless the management context is a non-functional
     * (non-deployment) instance. */
    String getManagementPlaneId();
    
    /** 
     * UID for this {@link ManagementContext} node (as part of a single management plane).
     * <p>
     * No two instances of {@link ManagementContext} should ever have the same node UID. 
     * The value of {@link Application#getManagementContext()#getManagementNodeId()} may
     * change many times (in contrast to {@link #getManagementPlaneId()}). 
     * <p>
     * This value should not be null unless the management context is a non-functional
     * (non-deployment) instance. */
    String getManagementNodeId();

    /**
     * The URI that this management node's REST API is available at, or absent if the node's
     * API is unavailable.
     */
    Maybe<URI> getManagementNodeUri();

    /**
     * All applications under control of this management plane
     */
    Collection<Application> getApplications();

    /**
     * Returns the {@link EntityManager} instance for managing/querying entities.
     */
    EntityManager getEntityManager();
    
    /**
     * Returns the {@link ExecutionManager} instance for entities and users in this management realm 
     * to submit tasks and to observe what tasks are occurring
     */
    ExecutionManager getExecutionManager();
    
    /** 
     * Returns an {@link ExecutionContext} within the {@link ExecutionManager} for tasks
     * associated to the Brooklyn node's operation (not any entities). 
     **/
    ExecutionContext getServerExecutionContext();

    /**
     * Returns the {@link EntityDriverManager} entities can use to create drivers. This
     * manager can also be used to programmatically customize which driver type to use 
     * for entities in different locations.
     * 
     * The default strategy for choosing a driver is to use a naming convention: 
     * {@link DriverDependentEntity#getDriverInterface()} returns the interface that the
     * driver must implement; its name should end in "Driver". For example, this suffix is 
     * replaced with "SshDriver" for SshMachineLocation, for example.
     */
    EntityDriverManager getEntityDriverManager();

    /**
     * Returns the {@link DownloadResolverManager} for resolving things like which URL to download an installer from.
     * 
     * The default {@link DownloadResolverManager} will retrieve {@code entity.getAttribute(Attributes.DOWNLOAD_URL)},
     * and substitute things like "${version}" etc.
     * 
     * However, additional resolvers can be registered to customize this behaviour (e.g. to always go to an 
     * enterprise's repository).
     */
    DownloadResolverManager getEntityDownloadsManager();

    /**
     * Returns the {@link SubscriptionManager} instance for entities and users of this management realm
     * to subscribe to sensor events (and, in the case of entities, to emit sensor events) 
     */
    SubscriptionManager getSubscriptionManager();

    //TODO (Alex) I'm not sure the following two getXxxContext methods are needed on the interface;
    //I expect they will only be called once, in AbstractEntity, and fully capable
    //there of generating the respective contexts from the managers
    //(Litmus test will be whether there is anything in FederatedManagementContext
    //which requires a custom FederatedExecutionContext -- or whether BasicEC 
    //works with FederatedExecutionManager)
    /**
     * Returns an {@link ExecutionContext} instance representing tasks 
     * (from the {@link ExecutionManager}) associated with this entity, and capable 
     * of conveniently running such tasks which will be associated with that entity  
     */
    ExecutionContext getExecutionContext(Entity entity);
    
    /**
     * Returns a {@link SubscriptionContext} instance representing subscriptions
     * (from the {@link SubscriptionManager}) associated with this entity, and capable 
     * of conveniently subscribing on behalf of that entity  
     */
    SubscriptionContext getSubscriptionContext(Entity entity);

    @Beta // method may move to an internal interface; brooklyn users should not need to call this directly
    RebindManager getRebindManager();

    /**
     * @since 0.7.0
     */
    @Beta // method may move to an internal interface; brooklyn users should not need to call this directly
    HighAvailabilityManager getHighAvailabilityManager();
    
    /**
     * Returns the ConfigMap (e.g. BrooklynProperties) applicable to this management context.
     * Defaults to reading ~/.brooklyn/brooklyn.properties but configurable in the management context.
     */
    StringConfigMap getConfig();
    
    /**
     * Whether the management context has been initialized and not yet terminated.
     * This does not mean startup is entirely completed. See also {@link #isStartupComplete()}.
     */
    // TODO should we replace this with isNotYetTerminated() ??
    // and perhaps introduce isFullyRunning() which does (isStartupComplete() && isRunning()),
    // and/or move to a MgmtContextStatus subclass where errors can also be stored?
    public boolean isRunning();
    
    /**
     * Whether all startup tasks have completed. Previous to this point the management context is still usable 
     * (and hence {@link #isRunning()} returns true immediately after construction)
     * but some subsystems (e.g. persistence, OSGi, webapps, entities started at startup)
     * may not be available until this returns true.
     * <p>
     * Also see {@link #isStartupComplete()}.
     */
    @Beta  // see comment on isRunning() as items might move to a status handler
    public boolean isStartupComplete();

    /** Record of configured locations and location resolvers */
    LocationRegistry getLocationRegistry();
    
    /** Record of configured Brooklyn entities (and templates and policies) which can be loaded */
    BrooklynCatalog getCatalog();

    /** Returns the class loader to be used to load items. 
     * Temporary routine while catalog supports classloader-based and OSGi-based classloading. */
    @Beta
    ClassLoader getCatalogClassLoader();

    LocationManager getLocationManager();

    /**
     * For controlling access to operations - can be queried to find if an operation is allowed.
     * Callers should *not* cache the result of this method, but should instead always call
     * again to get the {@link AccessController}.
     */
    AccessController getAccessController();

    /**
     * Reloads locations from {@code brooklyn.properties}. Any changes will apply only to newly created applications
     */
    void reloadBrooklynProperties();

    /**
     * Listener for {@code brooklyn.properties} reload events.
     *
     * @see {@link #raddPropertiesReloadListenerPropertiesReloadListener)}
     * @see {@link #removePropertiesReloadListener(PropertiesReloadListener)}
     */
    interface PropertiesReloadListener extends Serializable {

        /** Called when {@code brooklyn.properties} is reloaded. */
        void reloaded();

    }
    
    /**
     * Registers a listener to be notified when brooklyn.properties is reloaded
     */
    void addPropertiesReloadListener(PropertiesReloadListener listener);
    
    /**
     * Deregisters a listener from brooklyn.properties reload notifications 
     */
    void removePropertiesReloadListener(PropertiesReloadListener listener);

    /**
     * Active entitlements checker instance.
     */
    EntitlementManager getEntitlementManager();
 
    /** As {@link #lookup(String, Class)} but not constraining the return type */
    public BrooklynObject lookup(String id);
    
    /** Finds an entity with the given ID known at this management context */
    // TODO in future support policies etc
    public <T extends BrooklynObject> T lookup(String id, Class<T> type); 

}