package brooklyn.internal.storage.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;

public class LiveMap<K, V> implements Map<K, V> {

    static interface Mutator<K,V> {
        public Map<K,V> refresh();
        public V put(K k, V v);
        public void putAll(Map<? extends K, ? extends V> vs);
        public V remove(Object o);
        public void clear();
    }
    
    private final Mutator<K,V> mutator;
    
    public LiveMap(Mutator<K,V> mutator) {
        this.mutator = mutator;
    }

    @Override
    public V get(Object key) {
        return mutator.refresh().get(key);
    }
    
    @Override
    public boolean isEmpty() {
        return mutator.refresh().isEmpty();
    }
    
    @Override
    public int size() {
        return mutator.refresh().size();
    }
    

    @Override
    public boolean containsKey(Object key) {
        return mutator.refresh().containsKey(key);
    }
    
    @Override
    public boolean containsValue(Object v) {
        return mutator.refresh().containsValue(v);
    }
    
    @Override
    public V put(K k, V v) {
        return mutator.put(k, v);
    }
    
    @Override
    public void putAll(Map<? extends K, ? extends V> vs) {
        mutator.putAll(vs);
    }
   
    @Override
    public V remove(Object k) {
        return mutator.remove(k);
    }
    
    @Override
    public void clear() {
        mutator.clear();
    }
    
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        LiveSet.Mutator<Map.Entry<K, V>> setMutator = new LiveSet.BasicMutator<Map.Entry<K, V>>() {
            @Override public Set<Map.Entry<K, V>> refresh() {
                return mutator.refresh().entrySet();
            }
            @Override public boolean remove(Object o) {
                return mutator.remove(((Map.Entry<K, V>)o).getKey()) != null;
            }
        };
        return new LiveSet<Map.Entry<K,V>>(setMutator);
    }

    @Override
    public Set<K> keySet() {
        LiveSet.Mutator<K> setMutator = new LiveSet.BasicMutator<K>() {
            @Override public Set<K> refresh() {
                return mutator.refresh().keySet();
            }
            @Override public boolean remove(Object o) {
                return mutator.remove(o) != null;
            }
        };
        return new LiveSet<K>(setMutator);
    }
    
    @Override
    public Collection<V> values() {
        // TODO Not supporting modification of values; and not using a live view of the collection
        return ImmutableList.copyOf(mutator.refresh().values());
    }
    
    
    @Override
    public boolean equals(Object other) {
        // TODO Too expensive to do lookup on every call?
        return other instanceof Map && mutator.refresh().equals(other);
    }
    
    @Override
    public int hashCode() {
        // TODO Too expensive to do lookup on every call?
        return mutator.refresh().hashCode();
    }
    
    @Override
    public String toString() {
        // TODO Too expensive to do lookup on every call?
        return mutator.refresh().toString();
    }
}
