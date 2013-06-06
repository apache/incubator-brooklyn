package brooklyn.internal.storage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public interface DataGrid {

    /**
     * If already exists with this id, returns it; otherwise creates a new atomic long stored
     * in the datagrid. It is atomic across all nodes/threads access it.
     */
    public AtomicLong createAtomicLong(String id);

    /**
     * If a map already exists with this id, returns it; otherwise creates a new map stored
     * in the datagrid.
     */
    <K,V> Map<K,V> createMap(String id);
}
