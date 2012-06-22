package brooklyn.entity;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext
import brooklyn.management.internal.AbstractManagementContext;

/** An entity for testing which has a local management context (not associated with an application) */
public class LocallyManagedEntity extends AbstractEntity {

    ManagementContext mgmt = new LocalManagementContext()
    
    public LocallyManagedEntity(Entity owner) {
        super(owner);
    }
    public LocallyManagedEntity(Map flags=[:], Entity owner=null) {
        super(flags, owner)
    }
    
    //for testing
    @Override
    public ManagementContext getManagementContext() {
        if (!getApplication()) return mgmt;
        return super.getManagementContext();
    }

}
