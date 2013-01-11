package brooklyn.management.internal;

import java.util.Collection;
import java.util.Collections;

import brooklyn.entity.Entity;
import brooklyn.management.EntityManager;

public class NonDeploymentEntityManager implements EntityManager {

    private final NonDeploymentManagementContext mgmt;
    
    public NonDeploymentEntityManager(NonDeploymentManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    @Override
    public Collection<Entity> getEntities() {
        return Collections.emptyList();
    }

    @Override
    public Entity getEntity(String id) {
        return null;
    }

    @Override
    public boolean isManaged(Entity entity) {
        return false;
    }

    @Override
    public void manage(Entity e) {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    @Override
    public void unmanage(Entity e) {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }
}
