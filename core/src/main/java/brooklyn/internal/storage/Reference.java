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

/**
 * A reference to a value, backed by the storage-medium.
 * 
 * @see BrooklynStorage#getReference(String)
 * 
 * @author aled
 */
public interface Reference<T> {

    // TODO We can add compareAndSet(T,T) as and when required
    
    T get();
    
    T set(T val);
    
    /**
     * @return true if the value is null; false otherwise.
     */
    boolean isNull();
    
    /**
     * Sets the value back to null. Similar to {@code set(null)}.
     */
    void clear();
    
    /**
     * @return true if the value equals the given parameter; false otherwise
     */
    boolean contains(Object other);
}
