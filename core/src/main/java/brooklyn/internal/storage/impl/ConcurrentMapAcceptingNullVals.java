package brooklyn.internal.storage.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractMap;
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

/**
 * A decorator for a ConcurrentMap that allows null values to be used.
 * 
 * However, {@link #values()} and {@link #entrySet()} return immutable snapshots
 * of the map's contents. This may be revisited in a future version.
 * 
 * @author aled
 */
public class ConcurrentMapAcceptingNullVals<K, V> implements ConcurrentMap<K, V> {

    private static enum Marker {
        NULL;
    }
    
    private final ConcurrentMap<K, V> delegate;

    public ConcurrentMapAcceptingNullVals(ConcurrentMap<K,V> delegate) {
        this.delegate = checkNotNull(delegate, "delegate");
    }
    
    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(toNonNullValue(value));
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        // Note that returns an immutable snapshot
        Set<Map.Entry<K, V>> result = new LinkedHashSet<Map.Entry<K, V>>(delegate.size());
        for (Map.Entry<K, V> entry : delegate.entrySet()) {
            result.add(new AbstractMap.SimpleEntry<K,V>(entry.getKey(), (V)fromNonNullValue(entry.getValue())));
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Collection<V> values() {
        // Note that returns an immutable snapshot
        List<V> result = new ArrayList<V>(delegate.size());
        for (V v : delegate.values()) {
            result.add((V)fromNonNullValue(v));
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public V get(Object key) {
        return (V) fromNonNullValue(delegate.get(key));
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public V put(K key, V value) {
        return (V) fromNonNullValue(delegate.put(key, (V) toNonNullValue(value)));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> vals) {
        for (Map.Entry<? extends K, ? extends V> entry : vals.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V remove(Object key) {
        return (V) fromNonNullValue(delegate.remove(key));
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return (V) fromNonNullValue(delegate.putIfAbsent(key, (V) toNonNullValue(value)));
    }

    @Override
    public boolean remove(Object key, Object value) {
        return delegate.remove(key, (V) toNonNullValue(value));
    }

    @Override
    public V replace(K key, V value) {
        return (V) fromNonNullValue(delegate.replace(key, (V) toNonNullValue(value)));
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return delegate.replace(key, (V) toNonNullValue(oldValue), (V) toNonNullValue(newValue));
    }
    
    private static class SetWithNullVals<T> implements Set<T> {

        private final Set<T> delegate;
        
        public SetWithNullVals(Set<T> delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public boolean add(T e) {
            return delegate.add(e); // unsupported; let delegate give exception
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            return delegate.addAll(c); // unsupported; let delegate give exception
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(toNonNullValue(o));
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object e : c) {
                if (!delegate.contains(toNonNullValue(e))) return false;
            }
            return true;
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public Iterator<T> iterator() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(toNonNullValue(o));
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean result = false;
            for (Object e : c) {
                result = result & delegate.remove(toNonNullValue(e));
            }
            return result;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean result = false;
            for (Iterator<T> iter = delegate.iterator(); iter.hasNext();) {
                T e = iter.next();
                if (!c.contains(fromNonNullValue(e))) {
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
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            // TODO Auto-generated method stub
            return null;
        }

    }
    
    private static Object toNonNullValue(Object value) {
        return (value != null) ? value : Marker.NULL;
    }

    private static Object fromNonNullValue(Object value) {
        return (value == Marker.NULL) ? null : value; 
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
}
