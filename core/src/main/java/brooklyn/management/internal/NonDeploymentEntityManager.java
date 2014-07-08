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
package brooklyn.management.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntityTypeRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;

import com.google.common.base.Predicate;

public class NonDeploymentEntityManager implements EntityManagerInternal {

    private final ManagementContext initialManagementContext;
    
    public NonDeploymentEntityManager(ManagementContext initialManagementContext) {
        this.initialManagementContext = initialManagementContext;
    }
    
    @Override
    public EntityTypeRegistry getEntityTypeRegistry() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().getEntityTypeRegistry();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" (with no initial management context supplied) is not valid for this operation.");
        }
    }
    
    @Override
    public <T extends Entity> T createEntity(EntitySpec<T> spec) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().createEntity(spec);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" (with no initial management context supplied) is not valid for this operation.");
        }
    }
    
    @Override
    public <T extends Entity> T createEntity(Map<?,?> config, Class<T> type) {
        return createEntity(EntitySpec.create(type).configure(config));
    }

    @Override
    public <T extends Policy> T createPolicy(PolicySpec<T> spec) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().createPolicy(spec);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" (with no initial management context supplied) is not valid for this operation.");
        }
    }
    
    @Override
    public <T extends Enricher> T createEnricher(EnricherSpec<T> spec) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().createEnricher(spec);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" (with no initial management context supplied) is not valid for this operation.");
        }
    }
    
    @Override
    public Collection<Entity> getEntities() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().getEntities();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<Entity> getEntitiesInApplication(Application application) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().getEntitiesInApplication(application);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<Entity> findEntities(Predicate<? super Entity> filter) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().findEntities(filter);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<Entity> findEntitiesInApplication(Application application, Predicate<? super Entity> filter) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().findEntitiesInApplication(application, filter);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Entity getEntity(String id) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntityManager().getEntity(id);
        } else {
            return null;
        }
    }

    @Override
    public boolean isManaged(Entity entity) {
        return false;
    }

    @Override
    public void manage(Entity e) {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot manage "+e);
    }

    @Override
    public void unmanage(Entity e) {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot unmanage "+e);
    }
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }

    @Override
    public Iterable<Entity> getAllEntitiesInApplication(Application application) {
        if (isInitialManagementContextReal()) {
            return ((EntityManagerInternal)initialManagementContext.getEntityManager()).getAllEntitiesInApplication(application);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" (with no initial management context supplied) is not valid for this operation.");
        }
    }
}
