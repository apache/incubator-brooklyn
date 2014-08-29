/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;

public class MutableList<V> extends ArrayList<V> {
    private static final long serialVersionUID = -5533940507175152491L;

    private static final Logger log = LoggerFactory.getLogger(MutableList.class);
    
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

    public static <V> MutableList<V> copyOf(@Nullable Iterable<? extends V> orig) {
        return (orig instanceof Collection)
                ? new MutableList<V>((Collection<? extends V>)orig)
                : orig!=null ? new MutableList<V>(orig) : new MutableList<V>();
    }

    public static <E> MutableList<E> copyOf(Iterator<? extends E> elements) {
        if (!elements.hasNext()) {
            return of();
        }
        return new MutableList.Builder<E>().addAll(elements).build();
    }

    public MutableList() {
    }

    public MutableList(Collection<? extends V> source) {
        super(source);
    }

    public MutableList(Iterable<? extends V> source) {
        for (V s : source) {
            add(s);
        }
    }
    
    /** @deprecated since 0.7.0, use {@link #asImmutableCopy()}, or {@link #asUnmodifiable()} / {@link #asUnmodifiableCopy()} */ @Deprecated
    public ImmutableList<V> toImmutable() {
        return ImmutableList.copyOf(this);
    }
    /** creates an {@link ImmutableList} which is a copy of this list.  note that the list should not contain nulls.  */
    public List<V> asImmutableCopy() {
        try {
            return ImmutableList.copyOf(this);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Error converting list to Immutable, using unmodifiable instead: "+e, e);
            return asUnmodifiableCopy();
        }
    }
    /** creates a {@link Collections#unmodifiableList(List)} wrapper around this list. the method is efficient,
     * as there is no copying, but the returned view might change if the list here is changed.  */
    public List<V> asUnmodifiable() {
        return Collections.unmodifiableList(this);
    }
    /** creates a {@link Collections#unmodifiableList(List)} of a copy of this list.
     * the returned item is immutable, but unlike {@link #asImmutableCopy()} nulls are permitted. */
    public List<V> asUnmodifiableCopy() {
        return Collections.unmodifiableList(MutableList.copyOf(this));
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

        public Builder<V> addAll(Iterator<? extends V> iter) {
            while (iter.hasNext()) {
                add(iter.next());
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
        if (items!=null)
            for (V item: items) add(item);
        return this;
    }
    /** as {@link List#addAll(Collection)} but fluent style */
    public MutableList<V> appendAll(Iterator<? extends V> items) {
        addAll(items);
        return this;
    }

    public boolean addAll(Iterable<? extends V> setToAdd) {
        // copy of parent, but accepting Iterable and null
        if (setToAdd==null) return false;
        return addAll(setToAdd.iterator());
    }
    public boolean addAll(Iterator<? extends V> setToAdd) {
        if (setToAdd==null) return false;
        boolean modified = false;
        while (setToAdd.hasNext()) {
            if (add(setToAdd.next()))
                modified = true;
        }
        return modified;
    }

    public boolean removeIfNotNull(V item) {
        if (item==null) return false;
        return remove(item);
    }

}
