package brooklyn.util;

import java.util.Collection;
import java.util.LinkedHashSet;

import com.google.common.collect.ImmutableSet;

public class MutableSet<V> extends LinkedHashSet<V> {

    public static <V> MutableSet<V> of() {
        return new MutableSet<V>();
    }
    
    public static <V> MutableSet<V> of(V v1) {
        MutableSet<V> result = new MutableSet<V>();
        result.add(v1);
        return result;
    }
    
    public static <V> MutableSet<V> of(V v1, V v2) {
        MutableSet<V> result = new MutableSet<V>();
        result.add(v1);
        result.add(v2);
        return result;
    }
    
    public static <V> MutableSet<V> of(V v1, V v2, V v3) {
        MutableSet<V> result = new MutableSet<V>();
        result.add(v1);
        result.add(v2);
        result.add(v3);
        return result;
    }

    public static <V> MutableSet<V> copyOf(Iterable<? extends V> orig) {
        return new MutableSet<V>(orig);
    }
    
    public MutableSet() {
    }
    
    public MutableSet(Iterable<? extends V> source) {
        super((source instanceof Collection) ? (Collection<? extends V>)source : ImmutableSet.copyOf(source));
    }
    
    public ImmutableSet<V> toImmutable() {
        return ImmutableSet.copyOf(this);
    }
    
    public static <V> Builder<V> builder() {
        return new Builder<V>();
    }

    /**
     * @see guava's ImmutableSet.Builder
     */
    public static class Builder<V> {
        final MutableSet<V> result = new MutableSet<V>();

        public Builder() {}

        public Builder<V> add(V value) {
            result.add(value);
            return this;
        }

        public Builder<V> addAll(Iterable<? extends V> iterable) {
            if (iterable instanceof Collection) {
                result.addAll((Collection<? extends V>) iterable);
            } else {
                for (V v : iterable) {
                    result.add(v);
                }
            }
            return this;
        }

        public MutableSet<V> build() {
          return new MutableSet<V>(result);
        }
    }
}
