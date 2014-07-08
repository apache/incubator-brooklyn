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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.EffectorTasks.EffectorBodyTaskFactory;
import brooklyn.entity.effector.EffectorTasks.EffectorMarkingTaskFactory;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;
import brooklyn.management.TaskAdaptable;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class Effectors {

    private static final Logger log = LoggerFactory.getLogger(Effectors.class);
    
    public static class EffectorBuilder<T> {
        private Class<T> returnType;
        private String effectorName;
        private String description;
        private Map<String,ParameterType<?>> parameters = new LinkedHashMap<String,ParameterType<?>>();
        private EffectorTaskFactory<T> impl;
        
        private EffectorBuilder(Class<T> returnType, String effectorName) {
            this.returnType = returnType;
            this.effectorName = effectorName;
        }
        public EffectorBuilder<T> description(String description) {
            this.description = description;
            return this;                
        }
        public EffectorBuilder<T> parameter(Class<?> paramType, String paramName) {
            return parameter(paramType, paramName, null, null);
        }
        public EffectorBuilder<T> parameter(Class<?> paramType, String paramName, String paramDescription) {
            return parameter(paramType, paramName, paramDescription, null);                
        }
        public <V> EffectorBuilder<T> parameter(Class<V> paramType, String paramName, String paramDescription, V defaultValue) {
            return parameter(new BasicParameterType<V>(paramName, paramType, paramDescription, defaultValue));
        }
        public <V> EffectorBuilder<T> parameter(ConfigKey<V> key) {
            return parameter(asParameterType(key));
        }
        public EffectorBuilder<T> parameter(ParameterType<?> p) {
            // allow redeclaring, e.g. for the case where we are overriding an existing effector
            parameters.put(p.getName(), p);
            return this;
        }
        public EffectorBuilder<T> impl(EffectorTaskFactory<T> taskFactory) {
            this.impl = new EffectorMarkingTaskFactory<T>(taskFactory);
            return this;
        }
        public EffectorBuilder<T> impl(EffectorBody<T> effectorBody) {
            this.impl = new EffectorBodyTaskFactory<T>(effectorBody);
            return this;
        }
        /** returns the effector, with an implementation (required); @see {@link #buildAbstract()} */
        public Effector<T> build() {
             Preconditions.checkNotNull(impl, "Cannot create effector %s with no impl (did you forget impl? or did you mean to buildAbstract?)", effectorName);
             return new EffectorAndBody<T>(effectorName, returnType, ImmutableList.copyOf(parameters.values()), description, impl);
        }
        
        /** returns an abstract effector, where the body will be defined later/elsewhere 
         * (impl must not be set) */
        public Effector<T> buildAbstract() {
            Preconditions.checkArgument(impl==null, "Cannot create abstract effector {} as an impl is defined", effectorName);
            return new EffectorBase<T>(effectorName, returnType, ImmutableList.copyOf(parameters.values()), description);
        }
    }

    /** creates a new effector builder with the given name and return type */
    public static <T> EffectorBuilder<T> effector(Class<T> returnType, String effectorName) {
        return new EffectorBuilder<T>(returnType, effectorName);
    }

    /** creates a new effector builder to _override_ the given effector */
    public static <T> EffectorBuilder<T> effector(Effector<T> base) {
        EffectorBuilder<T> builder = new EffectorBuilder<T>(base.getReturnType(), base.getName());
        for (ParameterType<?> p: base.getParameters())
            builder.parameter(p);
        builder.description(base.getDescription());
        if (base instanceof EffectorWithBody)
            builder.impl(((EffectorWithBody<T>) base).getBody());
        return builder;
    }

    /** returns an unsubmitted task which invokes the given effector; use {@link Entities#invokeEffector(EntityLocal, Entity, Effector, Map)} for a submitted variant */
    public static <T> TaskAdaptable<T> invocation(Entity entity, Effector<T> eff, @Nullable Map<?,?> parameters) {
        @SuppressWarnings("unchecked")
        Effector<T> eff2 = (Effector<T>) ((EntityInternal)entity).getEffector(eff.getName());
        if (log.isTraceEnabled())
            log.trace("invoking "+eff+"/"+
                (eff instanceof EffectorWithBody<?> ? ((EffectorWithBody<?>)eff).getBody() : "bodyless")+
                " on entity " + entity+" "+
                (eff2==eff ? "" : " (actually "+eff2+"/"+
                        (eff2 instanceof EffectorWithBody<?> ? ((EffectorWithBody<?>)eff2).getBody() : "bodyless")+")"));
        if (eff2 != null) {
            if (eff2 != eff) {
                if (eff2 instanceof EffectorWithBody) {
                    log.debug("Replacing invocation of {} on {} with {} which is the impl defined at that entity", new Object[] { eff, entity, eff2 });
                    return ((EffectorWithBody<T>)eff2).getBody().newTask(entity, eff2, ConfigBag.newInstance().putAll(parameters));
                } else {
                    log.warn("Effector {} defined on {} has no body; invoking caller-supplied {} instead", new Object[] { eff2, entity, eff });
                }
            }
        } else {
            log.debug("Effector {} does not exist on {}; attempting to invoke anyway", new Object[] { eff, entity });
        }
        
        if (eff instanceof EffectorWithBody) {
            return ((EffectorWithBody<T>)eff).getBody().newTask(entity, eff, ConfigBag.newInstance().putAll(parameters));
        }
        
        throw new UnsupportedOperationException("No implementation registered for effector "+eff+" on "+entity);
    }    

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <V> ParameterType<V> asParameterType(ConfigKey<V> key) {
        return key.hasDefaultValue()
            ? new BasicParameterType<V>(key.getName(), (Class)key.getType(), key.getDescription(), key.getDefaultValue())
            : new BasicParameterType<V>(key.getName(), (Class)key.getType(), key.getDescription());
    }
    
    public static <V> ConfigKey<V> asConfigKey(ParameterType<V> paramType) {
        return ConfigKeys.newConfigKey(paramType.getParameterClass(), paramType.getName(), paramType.getDescription(), paramType.getDefaultValue());
    }

    /** returns an unsubmitted task which will invoke the given effector on the given entities;
     * return type is Task<List<T>> (but haven't put in the blood sweat toil and tears to make the generics work) */
    public static TaskAdaptable<List<?>> invocation(Effector<?> eff, Map<?,?> params, Iterable<? extends Entity> entities) {
        List<TaskAdaptable<?>> tasks = new ArrayList<TaskAdaptable<?>>();
        for (Entity e: entities) tasks.add(invocation(e, eff, params));
        return Tasks.parallel("invoking "+eff+" on "+tasks.size()+" node"+(Strings.s(tasks.size())), tasks.toArray(new TaskAdaptable[tasks.size()]));
    }

    /** returns an unsubmitted task which will invoke the given effector on the given entities
     * (this form of method is a convenience for {@link #invocation(Effector, Map, Iterable)}) */
    public static TaskAdaptable<List<?>> invocation(Effector<?> eff, MutableMap<?, ?> params, Entity ...entities) {
        return invocation(eff, params, Arrays.asList(entities));
    }
    
    public static boolean sameSignature(Effector<?> e1, Effector<?> e2) {
        return Objects.equal(e1.getName(), e2.getName()) &&
                Objects.equal(e1.getParameters(), e2.getParameters()) &&
                Objects.equal(e1.getReturnType(), e2.getReturnType());
    }
    
    // TODO sameSignatureAndBody
    
    public static boolean sameInstance(Effector<?> e1, Effector<?> e2) {
        return e1 == e2;
    }

}
