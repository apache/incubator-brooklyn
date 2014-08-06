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
package brooklyn.basic;

import java.io.Serializable;
import java.util.Set;

import brooklyn.config.ConfigKey;

/**
 * Gives type information for a {@link BrooklynObject}. It is an immutable snapshot.
 * 
 * It reflects a given brooklyn object at the time the snapshot was created: if anything
 * were added or removed on-the-fly then those changes will be included in subsequent
 * snapshots. Therefore instances of a given class could have different {@link BrooklynType}s.
 */
public interface BrooklynType extends Serializable {

    /**
     * The type name of this entity (normally the fully qualified class name).
     */
    String getName();
    
    /**
     * The simple type name of this entity (normally the unqualified class name).
     */
    String getSimpleName();

    /**
     * ConfigKeys available on this entity.
     */
    Set<ConfigKey<?>> getConfigKeys();
    
    /**
     * The ConfigKey with the given name, or null if not found.
     */
    ConfigKey<?> getConfigKey(String name);
}
