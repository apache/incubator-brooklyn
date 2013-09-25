package brooklyn.util.collections;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;

/** Map impl, exposing simple builder operations (add) in a fluent-style API,
 * where the final map is mutable.  You can also toImmutable. */
public class MutableMap<K,V> extends LinkedHashMap<K,V> {
    private static final long serialVersionUID = -2463168443382874384L;

    public static <K,V> MutableMap<K,V> of() {
        return new MutableMap<K,V>();
    }
    
    public static <K,V> MutableMap<K,V> of(K k1, V v1) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.put(k1, v1);
        return result;
    }
    
    public static <K,V> MutableMap<K,V> of(K k1, V v1, K k2, V v2) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.put(k1, v1);
        result.put(k2, v2);
        return result;
    }
    
    public static <K,V> MutableMap<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        return result;
    }

    public static <K,V> MutableMap<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3,K k4, V v4) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        return result;
    }

    public static <K,V> MutableMap<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3,K k4, V v4,K k5, V v5) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        result.put(k5, v5);
        return result;
    }

    public static <K,V> MutableMap<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3,K k4, V v4,K k5, V v5,K k6,V v6) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        result.put(k5, v5);
        result.put(k6, v6);
        return result;
    }

    public static <K,V> MutableMap<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3,K k4, V v4,K k5, V v5,K k6,V v6,K k7,V v7) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        result.put(k5, v5);
        result.put(k6, v6);
        result.put(k7, v7);
        return result;
     }

    public static <K,V> MutableMap<K,V> copyOf(Map<? extends K, ? extends V> orig) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.putAll(orig);
        return result;
    }
    
    public MutableMap() {}
    @SuppressWarnings("unchecked")
    public MutableMap(@SuppressWarnings("rawtypes") Map source) { super(source); }

    /** as {@link #put(Object, Object)} but fluent style */
    public MutableMap<K,V> add(K key, V value) {
        put(key, value);
        return this;
    }

    /** as {@link #putAll(Map)} but fluent style */
    public MutableMap<K,V> add(Map<K,V> m) {
        putAll(m);
        return this;
    }

    /** as {@link #put(Object, Object)} but excluding null values, and fluent style */
    public MutableMap<K,V> addIfNotNull(K key, V value) {
        if (value!=null) add(key, value);
        return this;
    }

    public ImmutableMap<K,V> toImmutable() {
        return ImmutableMap.copyOf(this);
    }
    
    public static <K, V> Builder<K, V> builder() {
        return new Builder<K,V>();
    }

    /**
     * @see guava's ImmutableMap.Builder
     */
    public static class Builder<K, V> {
        final MutableMap<K,V> result = new MutableMap<K,V>();

        public Builder() {}

        public Builder<K, V> put(K key, V value) {
            result.put(key, value);
            return this;
        }

        public Builder<K, V> putIfNotNull(K key, V value) {
            if (value!=null) result.put(key, value);
            return this;
        }

        public Builder<K, V> putIfAbsent(K key, V value) {
            if (!result.containsKey(key)) result.put(key, value);
            return this;
        }

        public Builder<K, V> put(Entry<? extends K, ? extends V> entry) {
            result.put(entry.getKey(), entry.getValue());
            return this;
        }

        public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
            result.putAll(map);
            return this;
        }

        public Builder<K, V> remove(K key) {
            result.remove(key);
            return this;
        }
        
        public Builder<K, V> removeAll(K... keys) {
            for (K key : keys) {
                result.remove(key);
            }
            return this;
        }

        public Builder<K, V> removeAll(Iterable<? extends K> keys) {
        	for (K key : keys) {
        		result.remove(key);
        	}
            return this;
        }

        /** moves the value stored under oldKey to newKey, if there was such a value */
        public Builder<K, V> renameKey(K oldKey, K newKey) {
            if (result.containsKey(oldKey)) {
                V oldValue = result.remove(oldKey);
                result.put(newKey, oldValue);
            }
            return this;
        }
        
        public MutableMap<K, V> build() {
          return new MutableMap<K,V>(result);
        }
    }

}
