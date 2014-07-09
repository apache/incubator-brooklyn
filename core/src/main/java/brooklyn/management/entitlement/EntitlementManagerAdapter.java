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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;

import brooklyn.entity.Entity;
import brooklyn.management.entitlement.Entitlements.EntityAndItem;

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
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> entitlementClass, T entitlementClassArgument) {
        if (log.isTraceEnabled()) {
            log.trace("Checking entitlement of "+context+" to "+entitlementClass+" "+entitlementClassArgument);
        }
        
        if (isEntitledToRoot( context )) return true;
        
        switch (Entitlements.EntitlementClassesEnum.of(entitlementClass)) {
        case ENTITLEMENT_SEE_ENTITY:
            return isEntitledToSeeEntity( context, (Entity)entitlementClassArgument );
            
        case ENTITLEMENT_SEE_SENSOR:
            return isEntitledToSeeSensor( context, (EntityAndItem<String>)entitlementClassArgument );
            
        case ENTITLEMENT_INVOKE_EFFECTOR:
            return isEntitledToInvokeEffector( context, (EntityAndItem<String>)entitlementClassArgument );
            
        case ENTITLEMENT_DEPLOY_APPLICATION:
            return isEntitledToDeploy( context, entitlementClassArgument );

        case ENTITLEMENT_SEE_ALL_SERVER_INFO:
            return isEntitledToSeeAllServerInfo( context );

        default:
            log.warn("Unsupported permission type: "+entitlementClass+" / "+entitlementClassArgument);
            return false;
        }
    }

    protected abstract boolean isEntitledToSeeSensor(EntitlementContext context, EntityAndItem<String> sensorInfo);
    protected abstract boolean isEntitledToSeeEntity(EntitlementContext context, Entity entity);
    protected abstract boolean isEntitledToInvokeEffector(EntitlementContext context, EntityAndItem<String> effectorInfo);
    protected abstract boolean isEntitledToDeploy(EntitlementContext context, Object app);
    protected abstract boolean isEntitledToSeeAllServerInfo(EntitlementContext context);
    protected abstract boolean isEntitledToRoot(EntitlementContext context);
    
}
