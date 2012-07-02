package brooklyn.util;

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

    public static <K,V> MutableMap<K,V> copyOf(Map<? extends K, ? extends V> orig) {
        MutableMap<K,V> result = new MutableMap<K,V>();
        result.putAll(orig);
        return result;
    }
    
    public MutableMap() {}
    public MutableMap(Map source) { super(source); }
    
    public MutableMap<K,V> add(K key, V value) {
        put(key, value);
        return this;
    }

    public MutableMap<K,V> add(Map<K,V> m) {
        putAll(m);
        return this;
    }

    public ImmutableMap<K,V> toImmutable() {
        return ImmutableMap.<K,V>builder().putAll(this).build();
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

        public Builder<K, V> put(Entry<? extends K, ? extends V> entry) {
            result.put(entry.getKey(), entry.getValue());
            return this;
        }

        public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
            result.putAll(map);
            return this;
        }

        public MutableMap<K, V> build() {
          return new MutableMap<K,V>(result);
        }
    }
}
