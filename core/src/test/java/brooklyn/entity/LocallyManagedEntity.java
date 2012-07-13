package brooklyn.entity;

import java.util.Map;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.MutableMap;

/** An entity for testing which has a local management context (not associated with an application) */
public class LocallyManagedEntity extends AbstractEntity {

    ManagementContext mgmt = new LocalManagementContext();
    
    public LocallyManagedEntity() {
        this(MutableMap.of(), null);
    }
    public LocallyManagedEntity(Map flags) {
        this(flags, null);
    }
    public LocallyManagedEntity(Entity owner) {
        this(MutableMap.of(), owner);
    }
    public LocallyManagedEntity(Map flags, Entity owner) {
        super(flags, owner);
    }
    
    //for testing
    @Override
    public ManagementContext getManagementContext() {
        if (getApplication() == null) return mgmt;
        return super.getManagementContext();
    }

}
