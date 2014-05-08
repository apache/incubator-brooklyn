package brooklyn.internal.storage.impl.inmemory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.impl.ConcurrentMapAcceptingNullVals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A simple implementation of datagrid backed by in-memory (unpersisted) maps, within a single JVM.
 * 
 * @author aled
 */
public class InmemoryDatagrid implements DataGrid {

    private final Map<String,Map<?,?>> maps = Maps.newLinkedHashMap();
    private final AtomicInteger creationCounter = new AtomicInteger();
    
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> ConcurrentMap<K, V> getMap(String id) {
        synchronized (maps) {
            ConcurrentMap<K, V> result = (ConcurrentMap<K, V>) maps.get(id);
            if (result == null) {
                result = newMap();
                maps.put(id, result);
                creationCounter.incrementAndGet();
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

    @Override
    public void terminate() {
        synchronized (maps) {
            maps.clear();
        }
    }

    @Override
    public Map<String, Object> getDatagridMetrics() {
        synchronized (maps) {
            return ImmutableMap.<String, Object>of("size", maps.size(), "createCount", creationCounter.get());
        }
    }

    @Override
    public Set<String> getKeys() {
        return maps.keySet();
    }
    
}
