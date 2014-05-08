package brooklyn.internal.storage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.google.common.annotations.VisibleForTesting;

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

    /**
     * Terminates the DataGrid. If there is a real datagrid with multiple machines running, it doesn't mean that the
     * datagrid is going to be terminated; it only means that all local resources of the datagrid are released.
     */
    void terminate();
    
    Map<String, Object> getDatagridMetrics();

    /** Returns snapshot of known keys at this datagrid */
    @VisibleForTesting
    Set<String> getKeys();
    
}
