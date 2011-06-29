package brooklyn.entity;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

/** An entity for testing which has a local management context (not associated with an application) */
public class LocallyManagedEntity extends AbstractEntity {

    ManagementContext mgmt = new LocalManagementContext()
    
    //for testing
    @Override
    public AbstractManagementContext getManagementContext() {
        if (!getApplication()) return mgmt;
        return super.getManagementContext();
    }

}
