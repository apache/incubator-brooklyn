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
package brooklyn.entity.group;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.trait.Startable;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class QuarantineGroupImpl extends AbstractGroupImpl implements QuarantineGroup {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class);

    @Override
    public void expungeMembers(boolean stopFirst) {
        Set<Entity> members = ImmutableSet.copyOf(getMembers());
        RuntimeException exception = null;
        if (stopFirst) {
            Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
            for (Entity member : members) {
                if (member instanceof Startable) {
                    Task<Void> task = Effectors.invocation(member, Startable.STOP, ImmutableMap.of()).asTask();
                    tasks.put(member, task);
                }
            }
            DynamicTasks.queueIfPossible(Tasks.parallel("stopping "+tasks.size()+" member"+Strings.s(tasks.size())+" (parallel)", tasks.values())).orSubmitAsync(this);
            try {
                waitForTasksOnExpungeMembers(tasks);
            } catch (RuntimeException e) {
                Exceptions.propagateIfFatal(e);
                exception = e;
                LOG.warn("Problem stopping members of quarantine group "+this+" (rethrowing after unmanaging members): "+e);
            }
        }
        for (Entity member : members) {
            removeMember(member);
            Entities.unmanage(member);
        }
        if (exception != null) {
            throw exception;
        }
    }
    
    // TODO Quite like DynamicClusterImpl.waitForTasksOnEntityStart
    protected Map<Entity, Throwable> waitForTasksOnExpungeMembers(Map<? extends Entity,? extends Task<?>> tasks) {
        // TODO Could have CompoundException, rather than propagating first
        Map<Entity, Throwable> errors = Maps.newLinkedHashMap();

        for (Map.Entry<? extends Entity,? extends Task<?>> entry : tasks.entrySet()) {
            Entity member = entry.getKey();
            Task<?> task = entry.getValue();
            try {
                task.get();
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            } catch (Throwable t) {
                Throwable interesting = Exceptions.getFirstInteresting(t);
                LOG.error("Quarantine group "+this+" failed to stop quarantined entity "+member+" (removing): "+interesting, interesting);
                LOG.debug("Trace for: Quarantine group "+this+" failed to stop quarantined entity "+member+" (removing): "+t, t);
                // previously we unwrapped but now there is no need I think
                errors.put(member, t);
            }
        }
        return errors;
    }
}
