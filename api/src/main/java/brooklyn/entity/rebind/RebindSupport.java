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
package brooklyn.entity.rebind;

import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.Memento;

/**
 * Supporter instance for behaviour related to rebinding a given entity/location/policy.
 * 
 * For example, the brooklyn framework may call {@code entity.getRebindSupport().getMemento()}
 * and persist this using a {@link BrooklynMementoPersister}. Later (e.g. after a brooklyn
 * restart) a new entity instance may be created and populated by the framework calling 
 * {@code entity.getRebindSupport().reconstruct(rebindContext, memento)}.
 * 
 * @author aled
 */
public interface RebindSupport<T extends Memento> {

    /**
     * Creates a memento representing this entity's current state. This is useful for when restarting brooklyn.
     */
    T getMemento();

    /**
     * Reconstructs this entity, given a memento of its state. Sets the internal state 
     * (including id and config keys), and sets the parent/children/locations of this entity.
     * 
     * Implementations should be very careful to not invoke or inspect these other entities/locations,
     * as they may also be being reconstructed at this time.
     * 
     * Called before rebind.
     */
    void reconstruct(RebindContext rebindContext, T memento);

    void addPolicies(RebindContext rebindContext, T Memento);
    
    void addEnrichers(RebindContext rebindContext, T Memento);
}
