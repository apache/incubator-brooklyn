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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.google.common.annotations.VisibleForTesting;

public interface DataGrid {

    /**
     * If a map already exists with this id, returns it; otherwise creates a new map stored
     * in the datagrid.
     */
    <K,V> ConcurrentMap<K,V> getMap(String id);

    /**
     * Deletes the map for this id, if it exists; otherwise a no-op.
     */
    void remove(String id);

    /**
     * Terminates the DataGrid. If there is a real datagrid with multiple machines running, it doesn't mean that the
     * datagrid is going to be terminated; it only means that all local resources of the datagrid are released.
     */
    void terminate();
    
    Map<String, Object> getDatagridMetrics();

    /** Returns snapshot of known keys at this datagrid */
    @VisibleForTesting
    Set<String> getKeys();
    
}
