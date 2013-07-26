package brooklyn.internal.storage;

import brooklyn.management.internal.ManagementContextInternal;

public interface BrooklynStorageFactory {
    BrooklynStorage newStorage(ManagementContextInternal managementContext);
}
