package brooklyn.internal.storage;

import java.util.Map;

public interface DataGrid {

    /**
     * If a map already exists with this id, returns it; otherwise creates a new map stored
     * in the datagrid.
     */
    <K,V> Map<K,V> getMap(String id);
}
