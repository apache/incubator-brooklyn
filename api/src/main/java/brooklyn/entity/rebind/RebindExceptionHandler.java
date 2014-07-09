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
package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

import com.google.common.annotations.Beta;

/**
 * Handler called on all exceptions to do with rebind.
 * 
 * @author aled
 */
@Beta
public interface RebindExceptionHandler {

    void onLoadBrooklynMementoFailed(String msg, Exception e);
    
    void onLoadLocationMementoFailed(String msg, Exception e);

    void onLoadEntityMementoFailed(String msg, Exception e);
    
    void onLoadPolicyMementoFailed(String msg, Exception e);
    
    void onLoadEnricherMementoFailed(String msg, Exception e);
    
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

    void onCreateLocationFailed(String locId, String locType, Exception e);

    void onCreateEntityFailed(String entityId, String entityType, Exception e);

    void onCreatePolicyFailed(String id, String type, Exception e);

    void onCreateEnricherFailed(String id, String type, Exception e);

    void onLocationNotFound(String id);
    
    void onEntityNotFound(String id);
    
    void onPolicyNotFound(String id);

    void onPolicyNotFound(String id, String context);

    void onEnricherNotFound(String id);

    void onEnricherNotFound(String id, String context);

    void onRebindEntityFailed(Entity entity, Exception e);

    void onRebindLocationFailed(Location location, Exception e);

    void onRebindPolicyFailed(Policy policy, Exception e);

    void onRebindEnricherFailed(Enricher enricher, Exception e);

    void onAddPolicyFailed(EntityLocal entity, Policy policy, Exception e);

    void onAddEnricherFailed(EntityLocal entity, Enricher enricher, Exception e);

    void onManageLocationFailed(Location location, Exception e);

    void onManageEntityFailed(Entity entity, Exception e);

    void onDone();
    
    RuntimeException onFailed(Exception e);
}
