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
package org.apache.brooklyn.core.mgmt.internal;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.catalog.internal.CatalogInitialization;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.internal.storage.BrooklynStorage;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.ha.OsgiManager;
import org.apache.brooklyn.core.mgmt.usage.UsageManager;
import org.apache.brooklyn.core.objs.proxy.InternalEntityFactory;
import org.apache.brooklyn.core.objs.proxy.InternalLocationFactory;
import org.apache.brooklyn.core.objs.proxy.InternalPolicyFactory;
import org.apache.brooklyn.util.core.task.TaskTags;
import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;

public interface ManagementContextInternal extends ManagementContext {

    public static final String SUB_TASK_TAG = TaskTags.SUB_TASK_TAG;
    
    public static final String EFFECTOR_TAG = BrooklynTaskTags.EFFECTOR_TAG;
    public static final String NON_TRANSIENT_TASK_TAG = BrooklynTaskTags.NON_TRANSIENT_TASK_TAG;
    public static final String TRANSIENT_TASK_TAG = BrooklynTaskTags.TRANSIENT_TASK_TAG;

    public static final String EMPTY_CATALOG_URL = "classpath://brooklyn/empty.catalog.bom";
    
    ClassLoader getBaseClassLoader();

    Iterable<URL> getBaseClassPathForScanning();

    void setBaseClassPathForScanning(Iterable<URL> urls);

    void setManagementNodeUri(URI uri);

    void addEntitySetListener(CollectionChangeListener<Entity> listener);

    void removeEntitySetListener(CollectionChangeListener<Entity> listener);

    void terminate();
    
    long getTotalEffectorInvocations();

    <T> T invokeEffectorMethodSync(final Entity entity, final Effector<T> eff, final Object args) throws ExecutionException;
    
    <T> Task<T> invokeEffector(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters);

    BrooklynStorage getStorage();
    
    BrooklynProperties getBrooklynProperties();
    
    AccessManager getAccessManager();

    UsageManager getUsageManager();
    
    /**
     * @return The OSGi manager, if available; may be absent if OSGi is not supported,
     * e.g. in test contexts (but will be supported in all major contexts).
     */
    Maybe<OsgiManager> getOsgiManager();

    InternalEntityFactory getEntityFactory();
    
    InternalLocationFactory getLocationFactory();
    
    InternalPolicyFactory getPolicyFactory();
    
    /**
     * Registers an entity that has been created, but that has not yet begun to be managed.
     * <p>
     * This differs from the idea of "preManaged" where the entities are in the process of being
     * managed, but where management is not yet complete.
     */
    // TODO would benefit from better naming! The name has percolated up from LocalEntityManager.
    //      should we just rename here as register or preManage?
    void prePreManage(Entity entity);

    /**
     * Registers a location that has been created, but that has not yet begun to be managed.
     */
    void prePreManage(Location location);

    /** Object which allows adding, removing, and clearing errors.
     * TODO In future this will change to a custom interface with a unique identifier for each error. */
    @Beta
    List<Throwable> errors();

    @Beta
    CatalogInitialization getCatalogInitialization();

    @Beta
    void setCatalogInitialization(CatalogInitialization catalogInitialization);

    @Beta
    ExternalConfigSupplierRegistry getExternalConfigProviderRegistry();

}
