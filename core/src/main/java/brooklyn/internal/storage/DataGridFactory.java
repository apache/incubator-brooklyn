package brooklyn.internal.storage;

import brooklyn.management.internal.ManagementContextInternal;

/**
 * A factory for creating a {@link DataGrid}.
 *
 * Implementations of this interface should have a public no arg constructor; this constructor will be
 * called through reflection in the {@link brooklyn.management.internal.LocalManagementContext}.
 */
public interface DataGridFactory {

    /**
     * Creates a {@link BrooklynStorage} instance.
     *
     * @param managementContext the ManagementContextInternal
     * @return the created BrooklynStorage.
     */
    DataGrid newDataGrid(ManagementContextInternal managementContext);
}