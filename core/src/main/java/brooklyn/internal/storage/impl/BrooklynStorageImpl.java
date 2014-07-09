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
package brooklyn.internal.storage.impl;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.Reference;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class BrooklynStorageImpl implements BrooklynStorage {

    private final DataGrid datagrid;
    private final ConcurrentMap<String, Object> refsMap;
    private final ConcurrentMap<String, Object> listsMap;
    private final ConcurrentMap<String, WeakReference<Reference<?>>> refsCache;
    private final ConcurrentMap<String, WeakReference<Reference<?>>> listRefsCache;
    
    public BrooklynStorageImpl(DataGrid datagrid) {
        this.datagrid = datagrid;
        this.refsMap = datagrid.getMap("refs");
        this.listsMap = datagrid.getMap("lists");
        this.refsCache = Maps.newConcurrentMap();
        this.listRefsCache = Maps.newConcurrentMap();
    }

    /**
     * Returns the DataGrid used by this  BrooklynStorageImpl
     *
     * @return the DataGrid.
     */
    @VisibleForTesting
    public DataGrid getDataGrid() {
        return datagrid;
    }

    @Override
    public <T> Reference<T> getReference(final String id) {
        // Can use different ref instances; no need to always return same one. Caching is an
        // optimisation to just avoid extra object creation.
        WeakReference<Reference<?>> weakRef = refsCache.get(id);
        Reference<?> ref = (weakRef != null) ? weakRef.get() : null;
        if (ref == null) {
            ref = new BackedReference<T>(refsMap, id) {
                @Override protected void finalize() {
                    // TODO Don't like using finalize due to performance overhead, but not
                    // optimising yet. Could use PhantomReference instead; see
                    // http://java.dzone.com/articles/finalization-and-phantom
                    refsCache.remove(id);
                }
            };
            refsCache.putIfAbsent(id, new WeakReference<Reference<?>>(ref));
        }
        return (Reference<T>) ref;
    }
    
    @Override
    public <T> Reference<List<T>> getNonConcurrentList(final String id) {
        WeakReference<Reference<?>> weakRef = listRefsCache.get(id);
        Reference<?> ref = (weakRef != null) ? weakRef.get() : null;
        if (ref == null) {
            ref = new BackedReference<List<T>>(listsMap, id) {
                @Override public List<T> get() {
                    List<T> result = super.get();
                    return (result == null ? ImmutableList.<T>of() : Collections.unmodifiableList(result));
                }
                @Override protected void finalize() {
                    listRefsCache.remove(id);
                }
            };
            listRefsCache.putIfAbsent(id, new WeakReference<Reference<?>>(ref));
        }
        return (Reference<List<T>>) ref;
    }

    @Override
    public <K, V> ConcurrentMap<K, V> getMap(final String id) {
        return datagrid.<K,V>getMap(id);
    }
    
    @Override
    public void remove(String id) {
        datagrid.remove(id);
        refsMap.remove(id);
        listsMap.remove(id);
        refsCache.remove(id);
        listRefsCache.remove(id);
    }

    @Override
    public void terminate() {
        datagrid.terminate();
    }
    
    public boolean isMostlyEmpty() {
        if (!refsMap.isEmpty() || !listsMap.isEmpty()) 
            return false;
        // the datagrid may have some standard bookkeeping entries
        return true;
    }
    
    @Override
    public Map<String, Object> getStorageMetrics() {
        return ImmutableMap.of(
                "datagrid", datagrid.getDatagridMetrics(),
                "refsMapSize", ""+refsMap.size(),
                "listsMapSize", ""+listsMap.size());
    }
    
    @Override
    public String toString() {
        return super.toString() + getStorageMetrics();
    }
}
