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
package brooklyn.entity.effector;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.task.DynamicSequentialTask;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * @since 0.6.0
 */
@Beta
public class EffectorTasks {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EffectorTasks.class);
    
    public interface EffectorTaskFactory<T> {
        public abstract TaskAdaptable<T> newTask(Entity entity, Effector<T> effector, ConfigBag parameters);
    }
    
    /** wrapper for {@link EffectorBody} which simply runs that body on each invocation;
     * the body must be thread safe and ideally stateless */
    public static class EffectorBodyTaskFactory<T> implements EffectorTaskFactory<T> {
        private final EffectorBody<T> effectorBody;
        public EffectorBodyTaskFactory(EffectorBody<T> effectorBody) {
            this.effectorBody = effectorBody;
        }
        
        @Override
        public Task<T> newTask(final Entity entity, final brooklyn.entity.Effector<T> effector, final ConfigBag parameters) {
            final AtomicReference<DynamicSequentialTask<T>> dst = new AtomicReference<DynamicSequentialTask<T>>();

            dst.set(new DynamicSequentialTask<T>(
                    getFlagsForTaskInvocationAt(entity, effector), 
                    new Callable<T>() {
                        @Override
                        public T call() throws Exception {
                            try {
                                DynamicTasks.setTaskQueueingContext(dst.get());
                                return effectorBody.call(parameters);
                            } finally {
                                DynamicTasks.removeTaskQueueingContext();
                            }
                        }
                    }) {
                        @Override
                        public void handleException(Throwable throwable) throws Exception {
                            EffectorUtils.handleEffectorException(entity, effector, throwable);
                        }
                    });
            return dst.get();
        };
        
        /** subclasses may override to add additional flags, but they should include the flags returned here 
         * unless there is very good reason not to; default impl returns a MutableMap */
        protected Map<Object,Object> getFlagsForTaskInvocationAt(Entity entity, Effector<?> effector) {
            return EffectorUtils.getTaskFlagsForEffectorInvocation(entity, effector);
        }
    }
    
    /** wrapper for {@link EffectorTaskFactory} which ensures effector task tags are applied to it if needed
     * (wrapping in a task if needed); without this, {@link EffectorBody}-based effectors get it by
     * virtue of the call to {@link #getFlagsForTaskInvocationAt(Entity, Effector)} therein
     * but {@link EffectorTaskFactory}-based effectors generate a task without the right tags
     * to be able to tell using {@link BrooklynTaskTags} the effector-context of the task 
     * <p>
     * this gets applied automatically so marked as package-private */
    static class EffectorMarkingTaskFactory<T> implements EffectorTaskFactory<T> {
        private final EffectorTaskFactory<T> effectorTaskFactory;
        public EffectorMarkingTaskFactory(EffectorTaskFactory<T> effectorTaskFactory) {
            this.effectorTaskFactory = effectorTaskFactory;
        }
        
        @Override
        public Task<T> newTask(final Entity entity, final brooklyn.entity.Effector<T> effector, final ConfigBag parameters) {
            if (effectorTaskFactory instanceof EffectorBodyTaskFactory)
                return effectorTaskFactory.newTask(entity, effector, parameters).asTask();
            // if we're in an effector context for this effector already, then also pass through
            if (BrooklynTaskTags.isInEffectorTask(Tasks.current(), entity, effector, false))
                return effectorTaskFactory.newTask(entity, effector, parameters).asTask();
            // otherwise, create the task inside an appropriate effector body so tags, name, etc are set correctly
            return new EffectorBodyTaskFactory<T>(new EffectorBody<T>() {
                @Override
                public T call(ConfigBag parameters) {
                    TaskAdaptable<T> t = DynamicTasks.queue(effectorTaskFactory.newTask(entity, effector, parameters));
                    return t.asTask().getUnchecked();
                }
            }).newTask(entity, effector, parameters);
        }
    }
    
    public static <T> ConfigKey<T> asConfigKey(ParameterType<T> t) {
        return ConfigKeys.newConfigKey(t.getParameterClass(), t.getName());
    }
    
    public static <T> ParameterTask<T> parameter(ParameterType<T> t) {
        return new ParameterTask<T>(asConfigKey(t)).
                name("parameter "+t);
    }
    public static <T> ParameterTask<T> parameter(Class<T> type, String name) {
        return new ParameterTask<T>(ConfigKeys.newConfigKey(type, name)).
                name("parameter "+name+" ("+type+")");
    }
    public static <T> ParameterTask<T> parameter(final ConfigKey<T> p) {
        return new ParameterTask<T>(p);
    }
    public static class ParameterTask<T> implements EffectorTaskFactory<T> {
        final ConfigKey<T> p;
        private TaskBuilder<T> builder;
        public ParameterTask(ConfigKey<T> p) {
            this.p = p;
            this.builder = Tasks.<T>builder().name("parameter "+p);
        }
        public ParameterTask<T> name(String taskName) {
            builder.name(taskName);
            return this;
        }
        @Override
        public Task<T> newTask(Entity entity, Effector<T> effector, final ConfigBag parameters) {
            return builder.body(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    return parameters.get(p);
                }
                
            }).build();
        }
        
    }

    public static <T> EffectorTaskFactory<T> of(final Task<T> task) {
        return new EffectorTaskFactory<T>() {
            @Override
            public Task<T> newTask(Entity entity, Effector<T> effector, ConfigBag parameters) {
                return task;
            }
        };
    }

    /** Finds the entity where this task is running
     * @throws NullPointerException if there is none (no task, or no context entity for that task) */
    public static Entity findEntity() {
        return Preconditions.checkNotNull(BrooklynTaskTags.getTargetOrContextEntity(Tasks.current()),
                "This must be executed in a task whose execution context has a target or context entity " +
                "(i.e. it must be run from within an effector)");
    }

    /** Finds the entity where this task is running, casted to the given Entity subtype
     * @throws NullPointerException if there is none
     * @throws IllegalArgumentException if it is not of the indicated type */
    public static <T extends Entity> T findEntity(Class<T> type) {
        Entity t = findEntity();
        return Reflections.cast(t, type);
    }

    /** Finds a unique {@link SshMachineLocation} attached to the entity 
     * where this task is running
     * @throws NullPointerException if {@link #findEntity()} fails
     * @throws IllegalStateException if call to {@link #getSshMachine(Entity)} fails */
    public static SshMachineLocation findSshMachine() {
        return getSshMachine(findEntity());
    }

    /** Finds a unique {@link SshMachineLocation} attached to the supplied entity 
     * @throws IllegalStateException if there is not a unique such {@link SshMachineLocation} */
    public static SshMachineLocation getSshMachine(Entity entity) {
        try {
            return Machines.findUniqueSshMachineLocation(entity.getLocations()).get();
        } catch (Exception e) {
            throw new IllegalStateException("Entity "+entity+" (in "+Tasks.current()+") requires a single SshMachineLocation, but has "+entity.getLocations(), e);
        }
    }

}
