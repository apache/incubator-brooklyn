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

public class AggregatingResourceLookup<T extends AbstractResource> extends AbstractResourceLookup<T> {

    List<ResourceLookup<T>> targets = new ArrayList<ResourceLookup<T>>();
    
    public static <T extends AbstractResource> AggregatingResourceLookup<T> of(ResourceLookup<T> ...targets) {
        AggregatingResourceLookup<T> result = new AggregatingResourceLookup<T>();
        for (ResourceLookup<T> item: targets) result.targets.add(item);
        return result;
    }
    
    public static <T extends AbstractResource> AggregatingResourceLookup<T> of(Iterable<ResourceLookup<T>> targets) {
        AggregatingResourceLookup<T> result = new AggregatingResourceLookup<T>();
        for (ResourceLookup<T> item: targets) result.targets.add(item);
        return result;        
    }

    public T get(String id) {
        for (ResourceLookup<T> item: targets) {
            T result = item.get(id);
            if (result!=null) return result;
        }
        return null;
    }

    public List<ResolvableLink<T>> links() {
        List<ResolvableLink<T>> result = new ArrayList<ResolvableLink<T>>();
        for (ResourceLookup<T> item: targets) result.addAll(item.links());
        return result;
    }
    
}
