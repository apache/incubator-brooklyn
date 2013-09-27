package brooklyn.util.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

        public Builder<V> add(V value1, V value2, V ...values) {
            result.add(value1);
            result.add(value2);
            for (V v: values) result.add(v);
            return this;
        }

        public Builder<V> remove(V val) {
            result.remove(val);
            return this;
        }
        
        /** @deprecated since 0.6.0 ambiguous with {@link #addAll(Iterable)}; 
         * use {@link #add(Object, Object, Object...)} */ 
        @Deprecated
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

        /** @deprecated since 0.6.0 ambiguous with {@link #removeAll(Iterable)}; 
         * use <code>removeAll(Arrays.asList(Object, Object, Object...))</code> */ 
        @Deprecated
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
    
    /** as {@link List#add(Object)} but fluent style */
    public MutableList<V> append(V item) {
        add(item);
        return this;
    }

    /** as {@link List#add(Object)} but excluding nulls, and fluent style */
    public MutableList<V> appendIfNotNull(V item) {
        if (item!=null) add(item);
        return this;
    }

    /** as {@link List#add(Object)} but accepting multiple, and fluent style */
    public MutableList<V> append(V item1, V item2, V ...items) {
        add(item1);
        add(item2);
        for (V item: items) add(item);
        return this;
    }

    /** as {@link List#add(Object)} but excluding nulls, accepting multiple, and fluent style */
    public MutableList<V> appendIfNotNull(V item1, V item2, V ...items) {
        if (item1!=null) add(item1);
        if (item2!=null) add(item2);
        for (V item: items) 
            if (item!=null) add(item);
        return this;
    }

    /** as {@link List#addAll(Collection)} but fluent style */
    public MutableList<V> appendAll(Iterable<? extends V> items) {
        for (V item: items) add(item);
        return this;
    }

}
