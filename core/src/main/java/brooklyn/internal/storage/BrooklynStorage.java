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
package brooklyn.internal.storage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

public interface BrooklynStorage {

    /**
     * Creates a reference to a value, backed by the storage-medium. If a reference with this 
     * name has already been created, then that existing reference will be returned.
     * 
     * The returned reference is a live view: changes made to the reference will be persisted, 
     * and changes that others make will be reflected in the reference.
     * 
     * The reference is thread-safe. No additional synchronization is required when getting/setting
     * the reference.
     * 
     * @param id
     */
    <T> Reference<T> getReference(String id);

    /**
     * Creates a list backed by the storage-medium. If a list with this name has already been
     * created, then that existing list will be returned.
     * 
     * The returned list is not a live view. Changes are made by calling reference.set(), and
     * the view is refreshed by calling reference.get(). Changes are thread-safe, but callers
     * must be careful not to overwrite other's changes. For example, the code below could overwrite
     * another threads changes that are made to the map between the call to get() and the subsequent
     * call to set().
     * 
     * <pre>
     * {@code
     * Reference<List<String>> ref = storage.<String>createNonConcurrentList("myid");
     * List<String> newval = ImmutableList.<String>builder().addAll(ref.get()).add("another").builder();
     * ref.set(newval);
     * }
     * </pre>
     * 
     * TODO Aled says: Is getNonConcurrentList necessary?
     *   The purpose of this method, rather than just using
     *   {@code Reference ref = getReference(id); ref.set(ImmutableList.of())}
     *   is to allow control of the serialization of the things inside the list 
     *   (e.g. switching the Location object to serialize a proxy object of some sort). 
     *   I don't want us to have to do deep inspection of every object being added to any map/ref. 
     *   Feels like we can use normal serialization unless the top-level object matches an 
     *   instanceof for special things like Entity, Location, etc.
     * 
     * Peter responds:
     *   What I'm a bit scared of is that we need to write some kind of meta serialization mechanism 
     *   on top of the mechanisms provided by e.g. Hazelcast or Infinispan. Hazelcast has a very 
     *   extensive serialization library where you can plug in all kinds of serialization mechanisms.
     * 
     * @param id
     */
    @Beta
    <T> Reference<List<T>> getNonConcurrentList(String id);
    
    /**
     * Creates a map backed by the storage-medium. If a map with this name has already been
     * created, then that existing map will be returned.
     * 
     * The returned map is a live view: changes made to the map will be persisted, and changes 
     * that others make will be reflected in the map.
     * 
     * The map is thread-safe: {@link Map#keySet()} etc will iterate over a snapshot view of the
     * contents.
     * 
     * @param id
     */
    <K,V> ConcurrentMap<K,V> getMap(String id);

    /**
     * Removes the data stored against this id, whether it is a map, ref or whatever.
     */
    void remove(String id);

    /**
     * Terminates the BrooklynStorage.
     */
    void terminate();

    /** asserts that some of the storage containers which should be empty are empty; 
     * this could do more thorough checks, but as it is it is useful to catch many leaks,
     * and the things which aren't empty it's much harder to check whether they should be empty!
     * <p>
     * not meant for use outwith tests */
    @VisibleForTesting
    public boolean isMostlyEmpty();
    
    Map<String, Object> getStorageMetrics();
}
