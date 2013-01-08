package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.management.EntityManager;
import brooklyn.management.ManagementContext;

public class LocalEntityManager implements EntityManager {

    // TODO Would be good if ManagementContext delegated to EntityManager, rather than 
    // the other way round. But this is an improvement for now in that it splits up the
    // responsibilities on the interface
    
    private final ManagementContext managementContext;
    
    public LocalEntityManager(LocalManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public Collection<Entity> getEntities() {
        return managementContext.getEntities();
    }

    @Override
    public Entity getEntity(String id) {
        return managementContext.getEntity(id);
    }

    @Override
    public boolean isManaged(Entity entity) {
        return managementContext.isManaged(entity);
    }

    @Override
    public void manage(Entity e) {
        managementContext.manage(e);
    }

    @Override
    public void unmanage(Entity e) {
        managementContext.unmanage(e);
    }
}
