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
package io.brooklyn.camp.spi.collection;

import io.brooklyn.camp.spi.AbstractResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import brooklyn.util.collections.MutableMap;

public class BasicResourceLookup<T extends AbstractResource> extends AbstractResourceLookup<T> {

    Map<String,T> items = new MutableMap<String,T>();
    Map<String,ResolvableLink<T>> links = new MutableMap<String,ResolvableLink<T>>();
    
    public T get(String id) {
        return items.get(id);
    }

    public synchronized List<ResolvableLink<T>> links() {
        return new ArrayList<ResolvableLink<T>>(links.values());
    }

    public synchronized void add(T item) {
        T old = items.put(item.getId(), item);
        if (old!=null) {
            items.put(old.getId(), old);
            throw new IllegalStateException("Already contains item for "+item.getId()+": "+old+" (adding "+item+")");
        }
        links.put(item.getId(), newLink(item.getId(), item.getName()));
    }
    
    public synchronized void addAll(T... items) {
        for (T item: items) add(item);
    }
    
    public synchronized T update(T item) {
        T old = items.put(item.getId(), item);
        links.put(item.getId(), newLink(item.getId(), item.getName()));
        return old;
    }
    
    public synchronized boolean remove(String id) {
        items.remove(id);
        return links.remove(id)!=null;
    }
    
    public static <T extends AbstractResource> BasicResourceLookup<T> of(T ...items) {
        BasicResourceLookup<T> result = new BasicResourceLookup<T>();
        for (T item: items) result.add(item);
        return result;
    }
}
