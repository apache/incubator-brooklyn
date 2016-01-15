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
package org.apache.brooklyn.core.effector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.effector.ParameterType;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.effector.EffectorTasks.EffectorTaskFactory;
import org.apache.brooklyn.core.mgmt.internal.EffectorUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicSequentialTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/**
 * The abstract {@link Effector} implementation.
 * 
 * The concrete subclass (often anonymous) will supply the {@link #call(Entity, Map)} implementation,
 * and the fields in the constructor.
 */
public abstract class AbstractEffector<T> extends EffectorBase<T> implements EffectorWithBody<T> {

    private static final long serialVersionUID = 1832435915652457843L;
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEffector.class);

    public AbstractEffector(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description) {
        super(name, returnType, parameters, description);
    }

    public abstract T call(Entity entity, @SuppressWarnings("rawtypes") Map parameters);

    /** Convenience for named-parameter syntax (needs map in first argument) */
    public T call(Entity entity) { return call(ImmutableMap.of(), entity); }

    /** Convenience for named-parameter syntax (needs map in first argument) */
    public T call(@SuppressWarnings("rawtypes") Map parameters, Entity entity) { return call(entity, parameters); }

    /** @deprecated since 0.7.0 use {@link #getFlagsForTaskInvocationAt(Entity, Effector, ConfigBag)} */ @Deprecated
    protected final Map<Object,Object> getFlagsForTaskInvocationAt(Entity entity) {
        return getFlagsForTaskInvocationAt(entity, this, null);
    }
    /** subclasses may override to add additional flags, but they should include the flags returned here 
     * unless there is very good reason not to */
    protected Map<Object,Object> getFlagsForTaskInvocationAt(Entity entity, Effector<T> effector, ConfigBag parameters) {
        return EffectorUtils.getTaskFlagsForEffectorInvocation(entity, effector, parameters);
    }
    
    /** not meant for overriding; subclasses should override the abstract {@link #call(Entity, Map)} method in this class */
    @Override
    public final EffectorTaskFactory<T> getBody() {
        return new EffectorTaskFactory<T>() {
            @Override
            public Task<T> newTask(final Entity entity, final Effector<T> effector, final ConfigBag parameters) {
                return new DynamicSequentialTask<T>(
                        getFlagsForTaskInvocationAt(entity, AbstractEffector.this, parameters),
                        new Callable<T>() {
                            @Override public T call() {
                                return AbstractEffector.this.call(parameters.getAllConfig(), entity);
                            }
                        });
            }
        };
    }

}
