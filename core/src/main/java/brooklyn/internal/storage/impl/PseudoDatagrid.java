package brooklyn.internal.storage.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import brooklyn.internal.storage.DataGrid;

import com.google.common.collect.Maps;

public class PseudoDatagrid implements DataGrid {

    private final Map<String,Map<?,?>> maps = Maps.newLinkedHashMap();

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Map<K, V> getMap(String id) {
        synchronized (maps) {
            Map<K, V> result = (Map<K, V>) maps.get(id);
            if (result == null) {
                result = newMap();
                maps.put(id, result);
            }
            return result;
        }
    }
    
    // TODO Not doing Maps.newConcurrentMap() because needs to store null values.
    // Easy to avoid for Refererence<?> but harder for entity ConfigMap where the user
    // can insert null values.
    // 
    // Could write a decorator that switches null values for a null marker, and back again.
    //
    private <K,V> Map<K,V> newMap() {
        return Collections.synchronizedMap(new HashMap<K, V>());
    }

    @Override
    public void remove(String id) {
        synchronized (maps) {
            maps.remove(id);
        }
    }
}
