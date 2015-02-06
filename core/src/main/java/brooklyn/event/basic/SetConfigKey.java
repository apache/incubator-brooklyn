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
package brooklyn.event.basic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableSet;

/** A config key representing a set of values. 
 * If a value is set using this *typed* key, it is _added_ to the set
 * (with a warning issued if a collection is passed in).
 * If a value is set against an equivalent *untyped* key which *is* a collection,
 * it will be treated as a set upon discovery and used as a base to which subkey values are added.
 * If a value is discovered against this key which is not a map or collection,
 * it is ignored.
 * <p>
 * To add all items in a collection, to add a collection as a single element, 
 * to clear the list, or to set a collection (clearing first), 
 * use the relevant {@link SetModification} in {@link SetModifications}.
 * <p>  
 * Specific values can be added in a replaceable way by referring to a subkey.
 */
//TODO Create interface
public class SetConfigKey<V> extends AbstractCollectionConfigKey<Set<? extends V>, Set<Object>, V> {

    private static final long serialVersionUID = 751024268729803210L;
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(SetConfigKey.class);

    public SetConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public SetConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public SetConfigKey(Class<V> subType, String name, String description, Set<? extends V> defaultValue) {
        super((Class)Set.class, subType, name, description, defaultValue);
    }

    @Override
    protected Set<Object> merge(boolean unmodifiable, Iterable<?>... sets) {
        MutableSet<Object> result = MutableSet.of();
        for (Iterable<?> set: sets) result.addAll(set);
        if (unmodifiable) return result.asUnmodifiable();
        return result;
    }
    
    public interface SetModification<T> extends StructuredModification<SetConfigKey<T>>, Set<T> {
    }
    
    public static class SetModifications extends StructuredModifications {
        /** when passed as a value to a SetConfigKey, causes each of these items to be added.
         * if you have just one, no need to wrap in a mod. */
        // to prevent confusion (e.g. if a set is passed) we require two objects here.
        public static final <T> SetModification<T> add(final T o1, final T o2, final T ...oo) {
            Set<T> l = new LinkedHashSet<T>();
            l.add(o1); l.add(o2);
            for (T o: oo) l.add(o);
            return new SetModificationBase<T>(l, false);
        }
        /** when passed as a value to a SetConfigKey, causes each of these items to be added */
        public static final <T> SetModification<T> addAll(final Collection<T> items) { 
            return new SetModificationBase<T>(items, false);
        }
        /** when passed as a value to a SetConfigKey, causes the items to be added as a single element in the set */
        public static final <T> SetModification<T> addItem(final T item) {
            return new SetModificationBase<T>(Collections.singleton(item), false);
        }
        /** when passed as a value to a SetConfigKey, causes the set to be cleared and these items added */
        public static final <T> SetModification<T> set(final Collection<T> items) { 
            return new SetModificationBase<T>(items, true);
        }
    }

    public static class SetModificationBase<T> extends LinkedHashSet<T> implements SetModification<T> {
        private static final long serialVersionUID = 2715025591272457705L;
        private final boolean clearFirst;
        public SetModificationBase(Collection<T> delegate, boolean clearFirst) {
            super(delegate);
            this.clearFirst = clearFirst;
        }
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Object applyToKeyInMap(SetConfigKey<T> key, Map target) {
            if (clearFirst) {
                StructuredModification<StructuredConfigKey> clearing = StructuredModifications.clearing();
                clearing.applyToKeyInMap(key, target);
            }
            for (T o: this) target.put(key.subKey(), o);
            return null;
        }
    }
}
