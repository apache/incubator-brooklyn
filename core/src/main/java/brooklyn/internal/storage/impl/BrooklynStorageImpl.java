package brooklyn.internal.storage.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.Reference;

public class BrooklynStorageImpl implements BrooklynStorage {

    private static final Object NULL = new Object();

    private final DataGrid datagrid;
    private final Map<String,Object> simpleMap;
    private Map<String, Object> refsMap;
    
    public BrooklynStorageImpl(DataGrid datagrid) {
        this.datagrid = datagrid;
        this.simpleMap = datagrid.createMap("simple");
        this.refsMap = datagrid.createMap("refs");
    }
    
    @Override
    public Object get(String id) {
        return simpleMap.get(id);
    }

    @Override
    public Object put(String id, Object value) {
        return simpleMap.put(id, value);
    }

    @Override
    public <T> Reference<T> createReference(final String id) {
        // FIXME Use of NULL is necessary for ConcurrentHashMap that doesn't accept null values; but
        // won't work for a real datagrid backend!
        
        // TODO What synchronization do we want to guarantee "happens-before" for get/set calls in different threads?
        
        return new Reference<T>() {
            private final Object syncgate = new Object(); // to guarantee happens-before, for different threads calling get & set
            
            @Override public T get() {
                synchronized (syncgate) {}
                T result = (T) refsMap.get(id);
                return (result == NULL ? null : result);
            }
            @Override public T set(T val) {
                synchronized (syncgate) {}
                return (T) refsMap.put(id, (val != null ? val : NULL));
            }
            @Override
            public String toString() {
                return ""+get();
            }
        };
    }
    
    @Override
    public <T> Set<T> createSet(final String id) {
        LiveSet.Mutator<T> mutator = new LiveSet.Mutator<T>() {
            private Map<T,Boolean> map() {
                return datagrid.<T,Boolean>createMap(id);
            }
            @Override public Set<T> refresh() {
                return map().keySet();
            }
            @Override public boolean add(T o) {
                return map().put(o, true) != null;
            }
            @Override public boolean addAll(Collection<? extends T> c) {
                Map<T,Boolean> map = map();
                boolean modified = false;
                for (T element : c) {
                    Boolean pref = map.put(element, true);
                    modified = modified || (pref != null);
                }
                return modified;
            }
            @Override public boolean remove(Object o) {
                return map().remove(o) != null;
            }
            @Override public boolean removeAll(Collection<?> c) {
                return map().keySet().removeAll(c);
            }
            @Override public boolean retainAll(Collection<?> c) {
                return map().keySet().retainAll(c);
            }
            @Override public void clear() {
                map().clear();
            }
        };
        return new LiveSet<T>(mutator);
    }

    @Override
    public <K, V> Map<K, V> createMap(final String id) {
        LiveMap.Mutator<K,V> mutator = new LiveMap.Mutator<K,V>() {
            private Map<K,V> map() {
                return datagrid.<K,V>createMap(id);
            }
            @Override public Map<K, V> refresh() {
                return map();
            }
            @Override public V put(K k, V v) {
                return map().put(k, v);
            }

            @Override public void putAll(Map<? extends K, ? extends V> m) {
                map().putAll(m);
            }

            @Override public V remove(Object o) {
                return map().remove(o);
            }
            @Override public void clear() {
                map().clear();
            }
        };
        return new LiveMap<K,V>(mutator);
    }
}
