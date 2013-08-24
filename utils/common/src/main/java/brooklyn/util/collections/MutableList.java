package brooklyn.util.collections;

import java.util.ArrayList;
import java.util.Collection;

import com.google.common.collect.ImmutableList;

public class MutableList<V> extends ArrayList<V> {
    private static final long serialVersionUID = -5533940507175152491L;

    public static <V> MutableList<V> of() {
        return new MutableList<V>();
    }
    
    public static <V> MutableList<V> of(V v1) {
        MutableList<V> result = new MutableList<V>();
        result.add(v1);
        return result;
    }
    
    public static <V> MutableList<V> of(V v1, V v2, V ...vv) {
        MutableList<V> result = new MutableList<V>();
        result.add(v1);
        result.add(v2);
        for (V v: vv) result.add(v);
        return result;
    }

    public static <V> MutableList<V> copyOf(Iterable<? extends V> orig) {
        return new MutableList<V>(orig);
    }
    
    public MutableList() {
    }
    
    public MutableList(Iterable<? extends V> source) {
        super((source instanceof Collection) ? (Collection<? extends V>)source : ImmutableList.copyOf(source));
    }
    
    public ImmutableList<V> toImmutable() {
        return ImmutableList.copyOf(this);
    }
    
    public static <V> Builder<V> builder() {
        return new Builder<V>();
    }

    /**
     * @see guava's ImMutableList.Builder
     */
    public static class Builder<V> {
        final MutableList<V> result = new MutableList<V>();

        public Builder() {}

        public Builder<V> add(V value) {
            result.add(value);
            return this;
        }

        public Builder<V> remove(V val) {
            result.remove(val);
            return this;
        }
        
        public Builder<V> addAll(V... values) {
            for (V v : values) {
                result.add(v);
            }
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

        public Builder<V> removeAll(Iterable<? extends V> iterable) {
            if (iterable instanceof Collection) {
                result.removeAll((Collection<? extends V>) iterable);
            } else {
                for (V v : iterable) {
                    result.remove(v);
                }
            }
            return this;
        }

        public Builder<V> removeAll(V... values) {
            for (V v : values) {
                result.remove(v);
            }
            return this;
        }

        public MutableList<V> build() {
          return new MutableList<V>(result);
        }
        
        public ImmutableList<V> buildImmutable() {
            return ImmutableList.copyOf(result);
        }
    }
    
    public MutableList<V> append(V ...items) {
        for (V item: items) add(item);
        return this;
    }

    public MutableList<V> appendAll(Iterable<? extends V> items) {
        for (V item: items) add(item);
        return this;
    }

}
