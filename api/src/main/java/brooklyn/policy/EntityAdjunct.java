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

import brooklyn.entity.trait.Identifiable;

/**
 * EntityAdjuncts are supplementary logic that can be attached to Entities, providing sensor enrichment
 * or enabling policy
 */
public interface EntityAdjunct extends Identifiable {
    /**
     * A unique id for this adjunct
     */
    @Override
    String getId();

    /**
     * Get the name assigned to this adjunct
     *
     * @return the name assigned to the adjunct
     */
    String getName();
    
    /**
     * Whether the adjunct is destroyed
     */
    boolean isDestroyed();
    
    /**
     * Whether the adjunct is available
     */
    boolean isRunning();
}
