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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.Sets;

/**
 * Creates a Set backed by a given map (using the map's {@link Map#keySet()} for the contents of the set).
 * <p>
 * As {@link Collections#newSetFromMap(Map)} and guava's {@link Sets#newSetFromMap(Map)}, but accepts
 * a non-empty map. Also supports others modifying the backing map simultaneously, if the backing map
 * is a ConcurrentMap.
 */
public class SetFromLiveMap<E> extends AbstractSet<E> implements Set<E>, Serializable {

    public static <E> Set<E> create(Map<E, Boolean> map) {
        return new SetFromLiveMap<E>(map);
    }

    private final Map<E, Boolean> m; // The backing map
    private transient Set<E> s; // Its keySet

    SetFromLiveMap(Map<E, Boolean> map) {
        m = map;
        s = map.keySet();
    }

    @Override
    public void clear() {
        m.clear();
    }

    @Override
    public int size() {
        return m.size();
    }

    @Override
    public boolean isEmpty() {
        return m.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return m.containsKey(o);
    }

    @Override
    public boolean remove(Object o) {
        return m.remove(o) != null;
    }

    @Override
    public boolean add(E e) {
        return m.put(e, Boolean.TRUE) == null;
    }

    @Override
    public Iterator<E> iterator() {
        return s.iterator();
    }

    @Override
    public Object[] toArray() {
        return s.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return s.toArray(a);
    }

    @Override
    public String toString() {
        return s.toString();
    }

    @Override
    public int hashCode() {
        return s.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return this == object || this.s.equals(object);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return s.containsAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return s.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return s.retainAll(c);
    }

    // addAll is the only inherited implementation
    @GwtIncompatible("not needed in emulated source")
    private static final long serialVersionUID = 0;

    @GwtIncompatible("java.io.ObjectInputStream")
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        s = m.keySet();
    }
}