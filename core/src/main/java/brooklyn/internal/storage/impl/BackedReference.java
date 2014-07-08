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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import brooklyn.internal.storage.Reference;

import com.google.common.base.Objects;

class BackedReference<T> implements Reference<T> {
    private final Map<String,? super T> backingMap;
    private final String key;
    
    BackedReference(Map<String,? super T> backingMap, String key) {
        this.backingMap = checkNotNull(backingMap, "backingMap");
        this.key = key;
    }
    
    @Override
    public T get() {
        // For happens-before (for different threads calling get and set), relies on 
        // underlying map (e.g. from datagrid) having some synchronization
        return (T) backingMap.get(key);
    }
    
    @Override
    public T set(T val) {
        if (val == null) {
            return (T) backingMap.remove(key);
        } else {
            return (T) backingMap.put(key, val);
        }
    }
    
    @Override
    public String toString() {
        return ""+get();
    }
    
    @Override
    public boolean isNull() {
        return get() == null;
    }
    
    @Override
    public void clear() {
        set(null);
    }
    
    @Override
    public boolean contains(Object other) {
        return Objects.equal(get(), other);
    }
}
