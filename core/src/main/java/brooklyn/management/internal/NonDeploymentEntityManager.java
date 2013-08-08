package brooklyn.management.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntityTypeRegistry;
import brooklyn.management.EntityManager;
import brooklyn.management.ManagementContext;

public class NonDeploymentEntityManager implements EntityManager {

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
    public Collection<Entity> getEntities() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntities();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Entity getEntity(String id) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getEntity(id);
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
}
