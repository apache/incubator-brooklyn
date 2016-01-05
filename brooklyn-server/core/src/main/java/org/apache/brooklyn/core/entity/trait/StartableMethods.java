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
package org.apache.brooklyn.core.entity.trait;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskTags;
import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class StartableMethods {

    public static final Logger log = LoggerFactory.getLogger(StartableMethods.class);
        
    private StartableMethods() {}

    /** Common implementation for start in parent nodes; just invokes start on all children of the entity */
    public static void start(EntityLocal e, Collection<? extends Location> locations) {
        log.debug("Starting entity "+e+" at "+locations);
        DynamicTasks.queueIfPossible(startingChildren(e, locations)).orSubmitAsync(e).getTask().getUnchecked();
    }
    
    /** Common implementation for stop in parent nodes; just invokes stop on all children of the entity */
    public static void stop(EntityLocal e) {
        log.debug("Stopping entity "+e);
        DynamicTasks.queueIfPossible(stoppingChildren(e)).orSubmitAsync(e).getTask().getUnchecked();
        if (log.isDebugEnabled()) log.debug("Stopped entity "+e);
    }

    /** Common implementation for restart in parent nodes; just invokes restart on all children of the entity */
    public static void restart(EntityLocal e) {
        log.debug("Restarting entity "+e);
        DynamicTasks.queueIfPossible(restartingChildren(e)).orSubmitAsync(e).getTask().getUnchecked();
        if (log.isDebugEnabled()) log.debug("Restarted entity "+e);
    }
    
    private static <T extends Entity> Iterable<T> filterStartableManagedEntities(Iterable<T> contenders) {
        return Iterables.filter(contenders, Predicates.and(Predicates.instanceOf(Startable.class), EntityPredicates.isManaged()));
    }

    public static void stopSequentially(Iterable<? extends Startable> entities) {
        List<Exception> exceptions = Lists.newArrayList();
        List<Startable> failedEntities = Lists.newArrayList();
        
        for (final Startable entity : entities) {
            if (!Entities.isManaged((Entity)entity)) {
                log.debug("Not stopping {} because it is not managed; continuing", entity);
                continue;
            }
            try {
                TaskAdaptable<Void> task = TaskTags.markInessential(Effectors.invocation((Entity)entity, Startable.STOP, Collections.emptyMap()));
                DynamicTasks.submit(task, (Entity)entity).getUnchecked();
            } catch (Exception e) {
                log.warn("Error stopping "+entity+"; continuing with shutdown", e);
                exceptions.add(e);
                failedEntities.add(entity);
            }
        }
        
        if (exceptions.size() > 0) {
            throw new CompoundRuntimeException("Error stopping "+(failedEntities.size() > 1 ? "entities" : "entity")+": "+failedEntities, exceptions);
        }
    }

    /** unsubmitted task for starting children of the given entity at the same location as the entity */
    public static TaskAdaptable<?> startingChildren(Entity entity) {
        return startingChildren(entity, entity.getLocations());
    }
    /** unsubmitted task for starting children of the given entity at the given location */
    public static TaskAdaptable<?> startingChildren(Entity entity, Location location) {
        return startingChildren(entity, Collections.singleton(location));
    }
    /** unsubmitted task for starting children of the given entity at the given locations */
    public static TaskAdaptable<?> startingChildren(Entity entity, Iterable<? extends Location> locations) {
        return Effectors.invocation(Startable.START, MutableMap.of("locations", locations), filterStartableManagedEntities(entity.getChildren()));
    }

    /** unsubmitted task for stopping children of the given entity */
    public static TaskAdaptable<?> stoppingChildren(Entity entity) {
        return Effectors.invocation(Startable.STOP, Collections.emptyMap(), filterStartableManagedEntities(entity.getChildren()));
    }

    /** unsubmitted task for restarting children of the given entity */
    public static TaskAdaptable<?> restartingChildren(Entity entity, ConfigBag parameters) {
        return Effectors.invocation(Startable.RESTART, parameters.getAllConfig(), filterStartableManagedEntities(entity.getChildren()));
    }
    /** as {@link #restartingChildren(Entity, ConfigBag)} with no parameters */
    public static TaskAdaptable<?> restartingChildren(Entity entity) {
        return restartingChildren(entity, ConfigBag.EMPTY);
    }

}
