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

import java.util.concurrent.atomic.AtomicReference;

import brooklyn.internal.storage.Reference;

import com.google.common.base.Objects;

public class BasicReference<T> implements Reference<T >{

    private final AtomicReference<T> ref = new AtomicReference<T>();
    
    public BasicReference() {
    }
    
    public BasicReference(T val) {
        set(val);
    }
    
    @Override
    public T get() {
        return ref.get();
    }

    @Override
    public T set(T val) {
        return ref.getAndSet(val);
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
    
    @Override
    public String toString() {
        return ""+get();
    }
}
