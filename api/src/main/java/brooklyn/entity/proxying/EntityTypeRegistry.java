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
package brooklyn.entity.proxying;

import brooklyn.entity.Entity;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.location.Location;

/**
 * A registry of the entity implementations to be used when creating an entity of a given type.
 * 
 * A given implementation can only be associated with one entity type interface.
 */
public interface EntityTypeRegistry {

    /**
     * Returns the implementation to be used for the given entity type.
     *
     * @param entity the {@link DriverDependentEntity} to create the {@link EntityDriver} for.
     * @param location the {@link Location} where the {@link DriverDependentEntity} is running.
     * @param <D>
     * @return the creates EntityDriver.
     * @throws IllegalArgumentException If no implementation registered, and the given interface is not annotated with {@link ImplementedBy}
     * @throws IllegalStateException If the given type is not an interface, or if the implementation class is not a concrete class implementing it
     */
    <T extends Entity> Class<? extends T> getImplementedBy(Class<T> type);

    /**
     * Returns the interface of this entity implementation.
     * E.g. for use as the fully qualified name in {@code entity.getEntityType().getName()}.
     * 
     * @throws IllegalArgumentException If no interface is registered against this implementation, 
     *         and no super-type of the class is annotated with {@link ImplementedBy} to point at the given class
     */
    <T extends Entity> Class<? super T> getEntityTypeOf(Class<T> type);

    /**
     * Registers the implementation to use for a given entity type.
     * 
     * The implementation must be a non-abstract class implementing the given type, and must 
     * have a no-argument constructor.
     * 
     * @throws IllegalArgumentException If this implementation has already been registered for a different type
     * @throws IllegalStateException If the implClazz is not a concrete class, or does not implement type
     */
    <T extends Entity> EntityTypeRegistry registerImplementation(Class<T> type, Class<? extends T> implClazz);
}
