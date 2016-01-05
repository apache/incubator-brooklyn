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
package org.apache.brooklyn.api.mgmt.rebind;

import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.rebind.mementos.EntityMemento;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynObjectType;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.Feed;

import com.google.common.annotations.Beta;
import org.apache.brooklyn.config.ConfigKey;

/**
 * Handler called on all exceptions to do with rebind.
 * A handler instance is linked to a single rebind pass;
 * it should not be invoked after {@link #onDone()}.
 * <p>
 * {@link #onStart()} must be invoked before the run.
 * {@link #onDone()} must be invoked after a successful run, and it may throw.
 * <p>
 * Implementations may propagate errors or may catch them until {@link #onDone()} is invoked,
 * and that may throw or report elsewhere, as appropriate.
 * 
 * @author aled
 */
@Beta
public interface RebindExceptionHandler {

    void onLoadMementoFailed(BrooklynObjectType type, String msg, Exception e);
    
    /**
     * @return the entity to use in place of the missing one, or null (if hasn't thrown an exception)
     */
    Entity onDanglingEntityRef(String id);

    /**
     * @return the location to use in place of the missing one, or null (if hasn't thrown an exception)
     */
    Location onDanglingLocationRef(String id);

    /**
     * @return the policy to use in place of the missing one, or null (if hasn't thrown an exception)
     */
    Policy onDanglingPolicyRef(String id);

    /**
     * @return the enricher to use in place of the missing one, or null (if hasn't thrown an exception)
     */
    Enricher onDanglingEnricherRef(String id);

    /**
     * @return the feed to use in place of the missing one, or null (if hasn't thrown an exception)
     */
    Feed onDanglingFeedRef(String id);
    
    /**
     * @return the catalog item to use in place of the missing one
     */
    CatalogItem<?, ?> onDanglingCatalogItemRef(String id);

    /**
     * @return the item to use in place of the missing one
     */
    BrooklynObject onDanglingUntypedItemRef(String id);

    void onCreateFailed(BrooklynObjectType type, String id, String instanceType, Exception e);

    void onNotFound(BrooklynObjectType type, String id);

    void onRebindFailed(BrooklynObjectType type, BrooklynObject instance, Exception e);

    void onAddConfigFailed(EntityMemento type, ConfigKey<?> value, Exception e);

    void onAddPolicyFailed(EntityLocal entity, Policy policy, Exception e);

    void onAddEnricherFailed(EntityLocal entity, Enricher enricher, Exception e);

    void onAddFeedFailed(EntityLocal entity, Feed feed, Exception e);

    void onManageFailed(BrooklynObjectType type, BrooklynObject instance, Exception e);

    /** invoked for any high-level, unexpected, or otherwise uncaught failure;
     * may be invoked on catching above errors */
    RuntimeException onFailed(Exception e);

    /** invoked before the rebind pass */
    void onStart(RebindContext context);
    
    /** invoked after the complete rebind pass, always on success and possibly on failure */
    void onDone();
    
    List<Exception> getExceptions();
    List<String> getWarnings();

}
