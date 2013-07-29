package brooklyn.internal.storage.impl.hazelcast;

import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.BrooklynStorageFactory;
import brooklyn.internal.storage.impl.BrooklynStorageImpl;
import brooklyn.management.internal.ManagementContextInternal;
import com.hazelcast.core.HazelcastInstance;

public class HazelcastBrooklynStorageFactory implements BrooklynStorageFactory {

    private HazelcastInstance hazelcastInstance;

    public HazelcastBrooklynStorageFactory(){
    }

    public HazelcastBrooklynStorageFactory(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public BrooklynStorage newStorage(ManagementContextInternal managementContext) {
        HazelcastDataGrid dataGrid = new HazelcastDataGrid(managementContext,hazelcastInstance);
        return new BrooklynStorageImpl(dataGrid);
    }
}
