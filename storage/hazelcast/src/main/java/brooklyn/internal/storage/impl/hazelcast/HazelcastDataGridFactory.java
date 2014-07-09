package brooklyn.internal.storage.impl.hazelcast;

import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.DataGridFactory;
import brooklyn.management.internal.ManagementContextInternal;

import com.hazelcast.core.HazelcastInstance;

public class HazelcastDataGridFactory implements DataGridFactory {

    private HazelcastInstance hazelcastInstance;

    public HazelcastDataGridFactory() {
    }

    public HazelcastDataGridFactory(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public DataGrid newDataGrid(ManagementContextInternal managementContext) {
        return new HazelcastDataGrid(managementContext,hazelcastInstance);
    }
}