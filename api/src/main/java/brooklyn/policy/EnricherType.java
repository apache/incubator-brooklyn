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
package brooklyn.policy;

import java.io.Serializable;
import java.util.Set;

import brooklyn.config.ConfigKey;

import com.google.common.annotations.Beta;

/**
 * Gives type information for an {@link Enricher}. It is immutable.
 * 
 * For enrichers that can support config keys etc being added on-the-fly,
 * then this EnricherType will be a snapshot and subsequent snapshots will
 * include the changes.
 * 
 * @since 0.6
 */
@Beta
public interface EnricherType extends Serializable {

    // TODO Consider merging this with PolicyType? Have a common super-type? It also has overlap with EntityType.
    
    /**
     * The type name of this policy (normally the fully qualified class name).
     */
    String getName();
    
    /**
     * ConfigKeys available on this policy.
     */
    Set<ConfigKey<?>> getConfigKeys();
    
    /**
     * The ConfigKey with the given name, or null if not found.
     */
    ConfigKey<?> getConfigKey(String name);
}
