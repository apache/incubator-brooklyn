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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.internal.storage.impl.ConcurrentMapAcceptingNullVals;
import brooklyn.util.collections.MutableList;

/** A config key representing a list of values. 
 * If a value is set on this key, it is _added_ to the list.
 * (With a warning is issued if a collection is passed in.)
 * If a value is set against an equivalent *untyped* key which *is* a collection,
 * it will be treated as a list upon discovery and used as a base to which subkey values are appended.
 * If a value is discovered against this key which is not a map or collection,
 * it is ignored.
 * <p>
 * To add all items in a collection, to add a collection as a single element, 
 * to clear the list, or to set a collection (clearing first), 
 * use the relevant {@link ListModification} in {@link ListModifications}.
 * <p>  
 * Specific values can be added in a replaceable way by referring to a subkey.
 * 
 * @deprecated since 0.6; use SetConfigKey. 
 * The ListConfigKey does not guarantee order when subkeys are used,
 * due to distribution and the use of the {@link ConcurrentMapAcceptingNullVals} 
 * as a backing store.
 * However the class will likely be kept around with tests for the time being
 * as we would like to repair this.
 */
//TODO Create interface
@Deprecated
public class ListConfigKey<V> extends AbstractCollectionConfigKey<List<? extends V>,List<Object>,V> {

    private static final long serialVersionUID = 751024268729803210L;
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ListConfigKey.class);
    
    public ListConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public ListConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ListConfigKey(Class<V> subType, String name, String description, List<? extends V> defaultValue) {
        super((Class)List.class, subType, name, description, (List<V>)defaultValue);
    }

    @Override
    protected List<Object> merge(boolean unmodifiable, Iterable<?>... sets) {
        MutableList<Object> result = MutableList.of();
        for (Iterable<?> set: sets) result.addAll(set);
        if (unmodifiable) return result.asUnmodifiable();
        return result;
    }

    public interface ListModification<T> extends StructuredModification<ListConfigKey<T>>, List<T> {
    }
    
    public static class ListModifications extends StructuredModifications {
        /** when passed as a value to a ListConfigKey, causes each of these items to be added.
         * if you have just one, no need to wrap in a mod. */
        // to prevent confusion (e.g. if a list is passed) we require two objects here.
        public static final <T> ListModification<T> add(final T o1, final T o2, final T ...oo) {
            List<T> l = new ArrayList<T>();
            l.add(o1); l.add(o2);
            for (T o: oo) l.add(o);
            return new ListModificationBase<T>(l, false);
        }
        /** when passed as a value to a ListConfigKey, causes each of these items to be added */
        public static final <T> ListModification<T> addAll(final Collection<T> items) { 
            return new ListModificationBase<T>(items, false);
        }
        /** when passed as a value to a ListConfigKey, causes the items to be added as a single element in the list */
        public static final <T> ListModification<T> addItem(final T item) {
            return new ListModificationBase<T>(Collections.singletonList(item), false);
        }
        /** when passed as a value to a ListConfigKey, causes the list to be cleared and these items added */
        public static final <T> ListModification<T> set(final Collection<T> items) { 
            return new ListModificationBase<T>(items, true);
        }
    }

    public static class ListModificationBase<T> extends ArrayList<T> implements ListModification<T> {
        private static final long serialVersionUID = 7131812294560446235L;
        private final boolean clearFirst;
        public ListModificationBase(Collection<T> delegate, boolean clearFirst) {
            super(delegate);
            this.clearFirst = clearFirst;
        }
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Object applyToKeyInMap(ListConfigKey<T> key, Map target) {
            if (clearFirst) {
                StructuredModification<StructuredConfigKey> clearing = StructuredModifications.clearing();
                clearing.applyToKeyInMap(key, target);
            }
            for (T o: this) target.put(key.subKey(), o);
            return null;
        }
    }
}
