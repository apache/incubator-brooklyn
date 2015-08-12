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
package brooklyn.entity.basic;

import org.apache.brooklyn.management.Task;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.time.Duration;

import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/** Generally useful tasks related to entities */
public class EntityTasks {

    /** creates an (unsubmitted) task which waits for the attribute to satisfy the given predicate,
     * returning false if it times out or becomes unmanaged */
    public static <T> Task<Boolean> testingAttributeEventually(Entity entity, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return DependentConfiguration.builder().attributeWhenReady(entity, sensor)
            .readiness(condition)
            .postProcess(Functions.constant(true))
            .timeout(timeout)
            .onTimeoutReturn(false)
            .onUnmanagedReturn(false)
            .build();
    }

    /** creates an (unsubmitted) task which waits for the attribute to satisfy the given predicate,
     * throwing if it times out or becomes unmanaged */
    public static <T> Task<Boolean> requiringAttributeEventually(Entity entity, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return DependentConfiguration.builder().attributeWhenReady(entity, sensor)
            .readiness(condition)
            .postProcess(Functions.constant(true))
            .timeout(timeout)
            .onTimeoutThrow()
            .onUnmanagedThrow()
            .build();
    }

    /** as {@link #testingAttributeEventually(Entity, AttributeSensor, Predicate, Duration) for multiple entities */
    public static <T> Task<Boolean> testingAttributeEventually(Iterable<Entity> entities, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return DependentConfiguration.builder().attributeWhenReadyFromMultiple(entities, sensor, condition)
            .postProcess(Functions.constant(true))
            .timeout(timeout)
            .onTimeoutReturn(false)
            .onUnmanagedReturn(false)
            .postProcessFromMultiple(CollectionFunctionals.all(Predicates.equalTo(true)))
            .build();
    }
    
    /** as {@link #requiringAttributeEventually(Entity, AttributeSensor, Predicate, Duration) for multiple entities */
    public static <T> Task<Boolean> requiringAttributeEventually(Iterable<Entity> entities, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return DependentConfiguration.builder().attributeWhenReadyFromMultiple(entities, sensor, condition)
            .postProcess(Functions.constant(true))
            .timeout(timeout)
            .onTimeoutThrow()
            .onUnmanagedThrow()
            .postProcessFromMultiple(CollectionFunctionals.all(Predicates.equalTo(true)))
            .build();
    }

}
