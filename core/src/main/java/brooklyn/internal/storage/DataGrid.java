package brooklyn.internal.storage;

import java.util.concurrent.ConcurrentMap;

public interface DataGrid {

    /**
     * If a map already exists with this id, returns it; otherwise creates a new map stored
     * in the datagrid.
     */
    <K,V> ConcurrentMap<K,V> getMap(String id);

    /**
     * Deletes the map for this id, if it exists; otherwise a no-op.
     */
    void remove(String id);
}
