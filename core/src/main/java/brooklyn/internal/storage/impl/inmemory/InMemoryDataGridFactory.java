package brooklyn.internal.storage.impl.inmemory;

import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.DataGridFactory;
import brooklyn.management.internal.ManagementContextInternal;

public class InMemoryDataGridFactory implements DataGridFactory {
    
    public static DataGridFactory ofInstance(final DataGrid datagrid) {
        return new DataGridFactory() {
            @Override
            public DataGrid newDataGrid(ManagementContextInternal managementContext) {
                return datagrid;
            }
        };
    }
    
    @Override
    public DataGrid newDataGrid(ManagementContextInternal managementContext) {
        return new InmemoryDatagrid();
    }
}