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

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

public interface ResourceLookup<T extends AbstractResource> {

    public abstract T get(String id);
    
    public abstract List<ResolvableLink<T>> links();
    
    public abstract boolean isEmpty();

    public static class EmptyResourceLookup<T extends AbstractResource> implements ResourceLookup<T> {
        public T get(String id) {
            throw new NoSuchElementException("no resource: "+id);
        }
        public List<ResolvableLink<T>> links() {
            return Collections.emptyList();
        }
        public boolean isEmpty() {
            return links().isEmpty();
        }
    }
    
}
