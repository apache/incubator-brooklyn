package brooklyn.internal.storage.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import brooklyn.internal.storage.Serializer;

/**
 * A decorator for a ConcurrentMap that allows null keys and values to be used.
 * 
 * It also accepts a serializer that transforms the underlying keys/values stored 
 * in the backing map.
 * 
 * However, {@link #values()} and {@link #entrySet()} return immutable snapshots
 * of the map's contents. This may be revisited in a future version.
 * 
 * @author aled
 */
public class ConcurrentMapAcceptingNullVals2<K, V> implements ConcurrentMap<K, V> {

    private static enum Marker {
        NULL;
    }
    
    private final ConcurrentMap<K, V> delegate;
    private final Serializer<Object,Object> serializer;

    public ConcurrentMapAcceptingNullVals2(ConcurrentMap<K,V> delegate, Serializer<Object,Object> serializer) {
        this.delegate = checkNotNull(delegate, "delegate");
        this.serializer = checkNotNull(serializer, "serializer");
    }
    
    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(toKey(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(toValue(value));
    }
    
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        // Note that returns an immutable snapshot
        Set<Map.Entry<K, V>> result = new LinkedHashSet<Map.Entry<K, V>>(delegate.size());
        for (Map.Entry<K, V> entry : delegate.entrySet()) {
            result.add(new AbstractMap.SimpleEntry<K,V>((K)fromKey(entry.getKey()), (V)fromValue(entry.getValue())));
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Collection<V> values() {
        // Note that returns an immutable snapshot
        List<V> result = new ArrayList<V>(delegate.size());
        for (V v : delegate.values()) {
            result.add((V)fromValue(v));
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public Set<K> keySet() {
        return new KeySet<K>(delegate.keySet());
    }

    @Override
    public V get(Object key) {
        return (V) fromValue(delegate.get(key));
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public V put(K key, V value) {
        return (V) fromValue(delegate.put((K)toKey(key), (V) toValue(value)));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> vals) {
        for (Map.Entry<? extends K, ? extends V> entry : vals.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V remove(Object key) {
        return (V) fromValue(delegate.remove(toKey(key)));
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return (V) fromValue(delegate.putIfAbsent((K)toKey(key), (V) toValue(value)));
    }

    @Override
    public boolean remove(Object key, Object value) {
        return delegate.remove((K) toKey(key), (V) toValue(value));
    }

    @Override
    public V replace(K key, V value) {
        return (V) fromValue(delegate.replace((K) toKey(key), (V) toValue(value)));
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return delegate.replace((K) toKey(key), (V) toValue(oldValue), (V) toValue(newValue));
    }
    
    private class KeySet<T> extends AbstractSet<T> {

        private final Set<T> delegate;
        
        KeySet(Set<T> delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(toKey(o));
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object e : c) {
                if (!delegate.contains(toKey(e))) return false;
            }
            return true;
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public Iterator<T> iterator() {
            // Note that returns an immutable snapshot
            List<T> result = new ArrayList<T>(delegate.size());
            for (T k : delegate) {
                result.add((T)fromKey(k));
            }
            return Collections.unmodifiableList(result).iterator();
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(toKey(o));
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean result = false;
            for (Object e : c) {
                result = result & delegate.remove(toKey(e));
            }
            return result;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean result = false;
            for (Iterator<T> iter = delegate.iterator(); iter.hasNext();) {
                T e = iter.next();
                if (!c.contains(fromKey(e))) {
                    iter.remove();
                    result = true;
                }
            }
            return result;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public Object[] toArray() {
            Object[] result = delegate.toArray();
            for (int i = 0; i < result.length; i++) {
                result[i] = fromKey(result[i]);
            }
            return result;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            // TODO Not type-safe if serializer is making changes
            T[] result = delegate.toArray(a);
            for (int i = 0; i < result.length; i++) {
                result[i] = (T) fromKey(result[i]);
            }
            return result;
        }

    }
    
    @Override
    public boolean equals(@Nullable Object object) {
        // copied from guava's non-public method Maps.equalsImpl
        if (this == object) {
            return true;
        }
        if (object instanceof Map) {
            Map<?, ?> o = (Map<?, ?>) object;
            return entrySet().equals(o.entrySet());
        }
        return false;
    }

    @Override
    public int hashCode() {
        // copied from guava's ImmutableMap.hashCode
        return entrySet().hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
    
    private Object toValue(Object value) {
        Object v2 = serializer.serialize(value);
        return ((v2 != null) ? v2 : Marker.NULL);
    }

    private Object fromValue(Object value) {
        Object v2 = (value == Marker.NULL) ? null : value; 
        return serializer.deserialize(v2);
    }
    
    private Object toKey(Object value) {
        Object v2 = serializer.serialize(value);
        return ((v2 != null) ? v2 : Marker.NULL);
    }

    private Object fromKey(Object value) {
        Object v2 = (value == Marker.NULL) ? null : value; 
        return serializer.deserialize(v2);
    }
}
