package brooklyn.internal.storage.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import brooklyn.internal.storage.DataGrid;

import com.google.common.collect.Maps;

public class PseudoDatagrid implements DataGrid {

    private final Map<String,Map<?,?>> maps = Maps.newLinkedHashMap();

    private final Map<String,AtomicLong> atomicLongs = Maps.newLinkedHashMap();

    @Override
    public AtomicLong createAtomicLong(String id) {
        synchronized (atomicLongs) {
            AtomicLong result = atomicLongs.get(id);
            if (result == null) {
                result = new AtomicLong();
                atomicLongs.put(id, result);
            }
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Map<K, V> createMap(String id) {
        synchronized (maps) {
            Map<K, V> result = (Map<K, V>) maps.get(id);
            if (result == null) {
                result = newMap();
                maps.put(id, result);
            }
            return result;
        }
    }
    
    // TODO Not doing Maps.newConcurrentMap() because needs to store null values
    private <K,V> Map<K,V> newMap() {
        return Collections.synchronizedMap(new HashMap<K, V>());
    }
}
