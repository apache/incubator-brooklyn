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
package brooklyn.management.entitlement;

import org.apache.brooklyn.management.entitlement.EntitlementClass;
import org.apache.brooklyn.management.entitlement.EntitlementContext;
import org.apache.brooklyn.management.entitlement.EntitlementManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;

import brooklyn.entity.Entity;
import brooklyn.management.entitlement.Entitlements.EntitlementClassesHandler;
import brooklyn.management.entitlement.Entitlements.EntityAndItem;
import brooklyn.management.entitlement.Entitlements.StringAndArgument;

/**
 * provides an easy entry point to supplying entitlements, by providing the dispatch and defining the additional methods
 * which have to be supplied.
 * <p>
 * note that this class may change as versions change, deliberately breaking backwards compatibility
 * to ensure all permissions are used.
 * <p>
 * @since 0.7.0 */
@Beta
public abstract class EntitlementManagerAdapter implements EntitlementManager {

    private static final Logger log = LoggerFactory.getLogger(EntitlementManagerAdapter.class);
    
    protected class Handler implements EntitlementClassesHandler<Boolean> {
        final EntitlementContext context;
        protected Handler(EntitlementContext context) {
            this.context = context;
        }
        
        @Override
        public Boolean handleSeeCatalogItem(String catalogItemId) {
            return isEntitledToSeeCatalogItem(context, catalogItemId);
        }
        @Override
        public Boolean handleAddCatalogItem(Object catalogItemBeingAdded) {
            return isEntitledToAddCatalogItem(context, catalogItemBeingAdded);
        }
        @Override
        public Boolean handleModifyCatalogItem(StringAndArgument catalogItemIdAndModification) {
            return isEntitledToModifyCatalogItem(context, catalogItemIdAndModification==null ? null : catalogItemIdAndModification.getString(),
                catalogItemIdAndModification==null ? null : catalogItemIdAndModification.getArgument());
        }
        
        @Override
        public Boolean handleSeeEntity(Entity entity) {
            return isEntitledToSeeEntity(context, entity);
        }
        @Override
        public Boolean handleSeeSensor(EntityAndItem<String> sensorInfo) {
            return isEntitledToSeeSensor(context, sensorInfo.getEntity(), sensorInfo.getItem());
        }
        @Override
        public Boolean handleInvokeEffector(EntityAndItem<StringAndArgument> effectorInfo) {
            StringAndArgument item = effectorInfo.getItem();
            return isEntitledToInvokeEffector(context, effectorInfo.getEntity(), item==null ? null : item.getString(), item==null ? null : item.getArgument());
        }
        @Override
        public Boolean handleModifyEntity(Entity entity) {
            return isEntitledToModifyEntity(context, entity);
        }

        @Override
        public Boolean handleDeployApplication(Object app) {
            return isEntitledToDeployApplication(context, app);
        }

        @Override
        public Boolean handleSeeAllServerInfo() {
            return isEntitledToSeeAllServerInfo(context);
        }

        @Override
        public Boolean handleSeeServerStatus() {
            return isEntitledToSeeServerStatus(context);
        }

        @Override
        public Boolean handleRoot() {
            return isEntitledToRoot(context);
        }
    }
    
    @Override
    public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> entitlementClass, T entitlementClassArgument) {
        if (log.isTraceEnabled()) {
            log.trace("Checking entitlement of "+context+" to "+entitlementClass+" "+entitlementClassArgument);
        }
        
        if (isEntitledToRoot( context )) return true;
        
        Handler handler = new Handler(context);
        return Entitlements.EntitlementClassesEnum.of(entitlementClass).handle(
            handler, entitlementClassArgument);
    }

    protected abstract boolean isEntitledToSeeCatalogItem(EntitlementContext context, String catalogItemId);
    /** passes item to be added, either yaml, or possibly null if any addition allowed (eg when resetting) */
    protected abstract boolean isEntitledToAddCatalogItem(EntitlementContext context, Object catalogItemBeingAdded);
    /** passes item being modified, as ID and description of modification, both possibly null if any modification is allowed (eg when resetting) */
    protected abstract boolean isEntitledToModifyCatalogItem(EntitlementContext context, String catalogItemId, Object catalogItemModification);
    protected abstract boolean isEntitledToSeeSensor(EntitlementContext context, Entity entity, String sensorName);
    protected abstract boolean isEntitledToSeeEntity(EntitlementContext context, Entity entity);
    /** arguments might be null, a map, or a list, depending how/where invoked */
    protected abstract boolean isEntitledToInvokeEffector(EntitlementContext context, Entity entity, String effectorName, Object arguments);
    protected abstract boolean isEntitledToModifyEntity(EntitlementContext context, Entity entity);
    protected abstract boolean isEntitledToDeployApplication(EntitlementContext context, Object app);
    protected abstract boolean isEntitledToSeeAllServerInfo(EntitlementContext context);
    protected abstract boolean isEntitledToSeeServerStatus(EntitlementContext context);
    protected abstract boolean isEntitledToRoot(EntitlementContext context);
    
}
