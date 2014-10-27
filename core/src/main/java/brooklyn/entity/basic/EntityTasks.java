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

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.management.TaskAdaptable;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.guava.Functionals;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/** Generally useful tasks related to entities */
public class EntityTasks {

    /** creates an (unsubmitted) task which waits for the attribute to satisfy the given predicate,
     * with an optional timeout */
    public static <T> TaskAdaptable<Boolean> awaitingAttribute(Entity entity, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return Tasks.awaitingBuilder(Repeater.create("waiting on "+sensor.getName())
                .backoff(Duration.millis(10), 1.5, Duration.millis(200))
                .limitTimeTo(timeout==null ? Duration.PRACTICALLY_FOREVER : timeout)
//                TODO abort if entity is unmanaged
                .until(Functionals.callable(Functions.forPredicate(EntityPredicates.attributeSatisfies(sensor, condition)), entity)),
                true)
            .description("waiting on "+entity+" "+sensor.getName()+" "+condition+
                (timeout!=null ? ", timeout "+timeout : "")).build();
    }

    /** as {@link #awaitingAttribute(Entity, AttributeSensor, Predicate, Duration)} for multiple entities */
    public static <T> TaskAdaptable<Boolean> awaitingAttribute(Iterable<Entity> entities, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return Tasks.awaitingBuilder(Repeater.create("waiting on "+sensor.getName())
                .backoff(Duration.millis(10), 1.5, Duration.millis(200))
                .limitTimeTo(timeout==null ? Duration.PRACTICALLY_FOREVER : timeout)
//                TODO abort if entity is unmanaged
                .until(Functionals.callable(Functions.forPredicate(
                    CollectionFunctionals.all(EntityPredicates.attributeSatisfies(sensor, condition))), entities)),
                true)
            .description("waiting on "+Iterables.size(entities)+", "+sensor.getName()+" "+condition+
                (timeout!=null ? ", timeout "+timeout : "")+
                ": "+entities).build();
    }
}
