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
package io.brooklyn.camp.brooklyn.spi.dsl;

import java.io.Serializable;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.resolve.interpret.PlanInterpretationNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.management.Task;
import brooklyn.management.TaskFactory;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DeferredSupplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** provide an object suitable to resolve chained invocations in a parsed YAML / Deployment Plan DSL,
 * which also implements {@link DeferredSupplier} so that they can be resolved when needed
 * (e.g. when entity-lookup and execution contexts are available).
 * <p>
 * implementations of this abstract class are expected to be immutable,
 * as instances must support usage in multiple {@link Assembly} instances 
 * created from a single {@link AssemblyTemplate}  
 * <p>
 * subclasses which return a deferred value are typically only
 * resolvable in the context of a {@link Task} on an {@link Entity}; 
 * these should be only used as the value of a {@link ConfigKey} set in the YAML,
 * and should not accessed until after the components / entities are created 
 * and are being started.
 * (TODO the precise semantics of this are under development.)
 * <p>
 **/
public abstract class BrooklynDslDeferredSupplier<T> implements DeferredSupplier<T>, TaskFactory<Task<T>>, Serializable {

    private static final long serialVersionUID = -8789624905412198233L;

    private static final Logger log = LoggerFactory.getLogger(BrooklynDslDeferredSupplier.class);
    
    // TODO json of this object should *be* this, not wrapped this ($brooklyn:literal is a bit of a hack, though it might work!)
    @JsonInclude
    @JsonProperty(value="$brooklyn:literal")
    // currently marked transient because it's only needed for logging
    private transient Object dsl = "(gone)";
    
    public BrooklynDslDeferredSupplier() {
        PlanInterpretationNode sourceNode = BrooklynDslInterpreter.currentNode();
        dsl = sourceNode!=null ? sourceNode.getOriginalValue() : null;
    }
    
    /** returns the current entity; for use in implementations of {@link #get()} */
    protected final static EntityInternal entity() {
        // rely on implicit ThreadLocal for now
        return (EntityInternal) EffectorTasks.findEntity();
    }

    @Override
    public final synchronized T get() {
        try {
            if (log.isDebugEnabled())
                log.debug("Queuing task to resolve "+dsl);
            T result = Entities.submit(entity(), newTask()).get();
            if (log.isDebugEnabled())
                log.debug("Resolved "+result+" from "+dsl);
            return result;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    public abstract Task<T> newTask();

}
