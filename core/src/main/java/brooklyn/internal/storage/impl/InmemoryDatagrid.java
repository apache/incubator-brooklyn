package brooklyn.internal.storage.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import brooklyn.internal.storage.DataGrid;

import com.google.common.collect.Maps;

/**
 * A simple implementation of datagrid backed by in-memory (unpersisted) maps, within a single JVM.
 * 
 * @author aled
 */
public class InmemoryDatagrid implements DataGrid {

    private final Map<String,Map<?,?>> maps = Maps.newLinkedHashMap();

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> ConcurrentMap<K, V> getMap(String id) {
        synchronized (maps) {
            ConcurrentMap<K, V> result = (ConcurrentMap<K, V>) maps.get(id);
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
    private <K,V> ConcurrentMap<K,V> newMap() {
        //return Collections.synchronizedMap(new HashMap<K, V>());
        return new ConcurrentMapAcceptingNullVals<K,V>(Maps.<K,V>newConcurrentMap());
    }

    @Override
    public void remove(String id) {
        synchronized (maps) {
            maps.remove(id);
        }
    }
}
