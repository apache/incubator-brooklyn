package brooklyn.internal.storage;

import brooklyn.management.internal.ManagementContextInternal;

/**
 * A factory for creating a {@link BrooklynStorage}.
 *
 * Implementations of this interface should have a public no arg constructor; this constructor will be
 * called through reflection in the {@link brooklyn.management.internal.LocalManagementContext}.
 */
public interface BrooklynStorageFactory {

    /**
     * Creates a {@link BrooklynStorage} instance.
     *
     * @param managementContext the ManagementContextInternal
     * @return the created BrooklynStorage.
     */
    BrooklynStorage newStorage(ManagementContextInternal managementContext);
}
