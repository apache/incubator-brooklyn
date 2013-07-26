package brooklyn.internal.storage.impl.inmemory;

import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.BrooklynStorageFactory;
import brooklyn.internal.storage.impl.BrooklynStorageImpl;
import brooklyn.management.internal.ManagementContextInternal;

public class InMemoryBrooklynStorageFactory implements BrooklynStorageFactory {
    @Override
    public BrooklynStorage newStorage(ManagementContextInternal managementContext) {
        InmemoryDatagrid dataGrid = new InmemoryDatagrid();
        return new BrooklynStorageImpl(dataGrid);
    }
}
