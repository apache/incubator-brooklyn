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
package org.apache.brooklyn.core.mgmt.internal;

import static org.apache.brooklyn.util.groovy.GroovyJavaMethods.truth;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.effector.ParameterType;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.effector.BasicParameterType;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Utility methods for invoking effectors.
 */
public class EffectorUtils {

    private static final Logger log = LoggerFactory.getLogger(EffectorUtils.class);

    /** prepares arguments for an effector either accepting:
     *  an array, which should contain the arguments in order, optionally omitting those which have defaults defined;
     *  or a map, which should contain the arguments by name, again optionally omitting those which have defaults defined,
     *  and in this case also performing type coercion.
     */
    public static Object[] prepareArgsForEffector(Effector<?> eff, Object args) {
        if (args != null && args.getClass().isArray()) {
            return prepareArgsForEffectorFromArray(eff, (Object[]) args);
        }
        if (args instanceof Map) {
            return prepareArgsForEffectorFromMap(eff, (Map) args);
        }
        log.warn("Deprecated effector invocation style for call to "+eff+", expecting a map or an array, got: "+args);
        if (log.isDebugEnabled()) {
            log.debug("Deprecated effector invocation style for call to "+eff+", expecting a map or an array, got: "+args,
                new Throwable("Trace for deprecated effector invocation style"));
        }
        return oldPrepareArgsForEffector(eff, args);
    }

    /** method used for calls such as   entity.effector(arg1, arg2)
     * get routed here from AbstractEntity.invokeMethod */
    private static Object[] prepareArgsForEffectorFromArray(Effector<?> eff, Object args[]) {
        int newArgsNeeded = eff.getParameters().size();
        if (args.length==1 && args[0] instanceof Map) {
            if (newArgsNeeded!=1 || !eff.getParameters().get(0).getParameterClass().isAssignableFrom(args[0].getClass())) {
                // treat a map in an array as a map passed directly (unless the method takes a single-arg map)
                // this is to support   effector(param1: val1)
                return prepareArgsForEffectorFromMap(eff, (Map) args[0]);
            }
        }
        return prepareArgsForEffectorAsMapFromArray(eff, args).values().toArray(new Object[0]);
    }

    public static Map prepareArgsForEffectorAsMapFromArray(Effector<?> eff, Object args[]) {
        int newArgsNeeded = eff.getParameters().size();
        List l = Lists.newArrayList();
        l.addAll(Arrays.asList(args));
        Map newArgs = new LinkedHashMap();

        for (int index = 0; index < eff.getParameters().size(); index++) {
            ParameterType<?> it = eff.getParameters().get(index);

            if (l.size() >= newArgsNeeded) {
                //all supplied (unnamed) arguments must be used; ignore map
                newArgs.put(it.getName(), l.remove(0));
                // TODO do we ignore arguments in the same order that groovy does?
            } else if (!l.isEmpty() && it.getParameterClass().isInstance(l.get(0))) {
                //if there are parameters supplied, and type is correct, they get applied before default values
                //(this is akin to groovy)
                newArgs.put(it.getName(), l.remove(0));
            } else if (it instanceof BasicParameterType && ((BasicParameterType)it).hasDefaultValue()) {
                //finally, default values are used to make up for missing parameters
                newArgs.put(it.getName(), ((BasicParameterType)it).getDefaultValue());
            } else {
                throw new IllegalArgumentException("Invalid arguments (count mismatch) for effector "+eff+": "+args);
            }

            newArgsNeeded--;
        }
        if (newArgsNeeded > 0) {
            throw new IllegalArgumentException("Invalid arguments (missing "+newArgsNeeded+") for effector "+eff+": "+args);
        }
        if (!l.isEmpty()) {
            throw new IllegalArgumentException("Invalid arguments ("+l.size()+" extra) for effector "+eff+": "+args);
        }
        return newArgs;
    }

    private static Object[] prepareArgsForEffectorFromMap(Effector<?> eff, Map m) {
        m = Maps.newLinkedHashMap(m); //make editable copy
        List newArgs = Lists.newArrayList();
        int newArgsNeeded = eff.getParameters().size();

        for (int index = 0; index < eff.getParameters().size(); index++) {
            ParameterType<?> it = eff.getParameters().get(index);
            Object v;
            if (truth(it.getName()) && m.containsKey(it.getName())) {
                // argument is in the map
                v = m.remove(it.getName());
            } else if (it instanceof BasicParameterType && ((BasicParameterType)it).hasDefaultValue()) {
                //finally, default values are used to make up for missing parameters
                v = ((BasicParameterType)it).getDefaultValue();
            } else {
                throw new IllegalArgumentException("Invalid arguments (missing argument "+it+") for effector "+eff+": "+m);
            }

            newArgs.add(TypeCoercions.coerce(v, it.getParameterClass()));
            newArgsNeeded--;
        }
        if (newArgsNeeded>0)
            throw new IllegalArgumentException("Invalid arguments (missing "+newArgsNeeded+") for effector "+eff+": "+m);
        return newArgs.toArray(new Object[newArgs.size()]);
    }

    /**
     * Takes arguments, and returns an array of arguments suitable for use by the Effector
     * according to the ParameterTypes it exposes.
     * <p>
     * The args can be:
     * <ol>
     * <li>an array of ordered arguments
     * <li>a collection (which will be automatically converted to an array)
     * <li>a single argument (which will then be wrapped in an array)
     * <li>a map containing the (named) arguments
     * <li>an array or collection single entry of a map (treated same as 5 above)
     * <li>a semi-populated array or collection that also containing a map as first arg -
     *     uses ordered args in array, but uses named values from map in preference.
     * <li>semi-populated array or collection, where default values will otherwise be used.
     * </ol>
     */
    public static Object[] oldPrepareArgsForEffector(Effector<?> eff, Object args) {
        //attempt to coerce unexpected types
        Object[] argsArray;
        if (args==null) {
            argsArray = new Object[0];
        } else if (args.getClass().isArray()) {
            argsArray = (Object[]) args;
        } else {
            if (args instanceof Collection) {
                argsArray = ((Collection) args).toArray(new Object[((Collection) args).size()]);
            } else {
                argsArray = new Object[] { args };
            }
        }

        //if args starts with a map, assume it contains the named arguments
        //(but only use it when we have insufficient supplied arguments)
        List l = Lists.newArrayList();
        l.addAll(Arrays.asList(argsArray));
        Map m = (argsArray.length > 0 && argsArray[0] instanceof Map ? Maps.newLinkedHashMap((Map) l.remove(0)) : null);
        List newArgs = Lists.newArrayList();
        int newArgsNeeded = eff.getParameters().size();
        boolean mapUsed = false;

        for (int index = 0; index < eff.getParameters().size(); index++) {
            ParameterType<?> it = eff.getParameters().get(index);

            if (l.size() >= newArgsNeeded) {
                //all supplied (unnamed) arguments must be used; ignore map
                newArgs.add(l.remove(0));
            } else if (truth(m) && truth(it.getName()) && m.containsKey(it.getName())) {
                //some arguments were not supplied, and this one is in the map
                newArgs.add(m.remove(it.getName()));
            } else if (index == 0 && Map.class.isAssignableFrom(it.getParameterClass())) {
                //if first arg is a map it takes the supplied map
                newArgs.add(m);
                mapUsed = true;
            } else if (!l.isEmpty() && it.getParameterClass().isInstance(l.get(0))) {
                //if there are parameters supplied, and type is correct, they get applied before default values
                //(this is akin to groovy)
                newArgs.add(l.remove(0));
            } else if (it instanceof BasicParameterType && ((BasicParameterType)it).hasDefaultValue()) {
                //finally, default values are used to make up for missing parameters
                newArgs.add(((BasicParameterType)it).getDefaultValue());
            } else {
                throw new IllegalArgumentException("Invalid arguments (count mismatch) for effector "+eff+": "+args);
            }

            newArgsNeeded--;
        }
        if (newArgsNeeded > 0) {
            throw new IllegalArgumentException("Invalid arguments (missing "+newArgsNeeded+") for effector "+eff+": "+args);
        }
        if (!l.isEmpty()) {
            throw new IllegalArgumentException("Invalid arguments ("+l.size()+" extra) for effector "+eff+": "+args);
        }
        if (truth(m) && !mapUsed) {
            throw new IllegalArgumentException("Invalid arguments ("+m.size()+" extra named) for effector "+eff+": "+args);
        }
        return newArgs.toArray(new Object[newArgs.size()]);
    }

    /**
     * Invokes a method effector so that its progress is tracked. For internal use only, when we know the effector is backed by a method which is local.
     */
    public static <T> T invokeMethodEffector(Entity entity, Effector<T> eff, Object[] args) {
        String name = eff.getName();

        try {
            if (log.isDebugEnabled()) log.debug("Invoking effector {} on {}", new Object[] {name, entity});
            if (log.isTraceEnabled()) log.trace("Invoking effector {} on {} with args {}", new Object[] {name, entity, args});
            EntityManagementSupport mgmtSupport = ((EntityInternal)entity).getManagementSupport();
            if (!mgmtSupport.isDeployed()) {
                mgmtSupport.attemptLegacyAutodeployment(name);
            }
            ManagementContextInternal mgmtContext = (ManagementContextInternal) ((EntityInternal) entity).getManagementContext();

            mgmtSupport.getEntityChangeListener().onEffectorStarting(eff, args);
            try {
                return mgmtContext.invokeEffectorMethodSync(entity, eff, args);
            } finally {
                mgmtSupport.getEntityChangeListener().onEffectorCompleted(eff);
            }
        } catch (Exception e) {
            handleEffectorException(entity, eff, e);
            // (won't return below)
            return null;
        }
    }

    public static void handleEffectorException(Entity entity, Effector<?> effector, Throwable throwable) {
        String message = "Error invoking " + effector.getName() + " at " + entity;
        // Avoid throwing a PropagatedRuntimeException that just repeats the last PropagatedRuntimeException.
        if (throwable instanceof PropagatedRuntimeException &&
                throwable.getMessage() != null &&
                throwable.getMessage().startsWith(message)) {
            throw PropagatedRuntimeException.class.cast(throwable);
        } else {
            log.warn(message + ": " + Exceptions.collapseText(throwable));
            throw new PropagatedRuntimeException(message, throwable);
        }
    }

    public static <T> Task<T> invokeEffectorAsync(Entity entity, Effector<T> eff, Map<String,?> parameters) {
        String name = eff.getName();

        if (log.isDebugEnabled()) log.debug("Invoking-async effector {} on {}", new Object[] { name, entity });
        if (log.isTraceEnabled()) log.trace("Invoking-async effector {} on {} with args {}", new Object[] { name, entity, parameters });
        EntityManagementSupport mgmtSupport = ((EntityInternal)entity).getManagementSupport();
        if (!mgmtSupport.isDeployed()) {
            mgmtSupport.attemptLegacyAutodeployment(name);
        }
        ManagementContextInternal mgmtContext = (ManagementContextInternal) ((EntityInternal)entity).getManagementContext();

        // FIXME seems brittle to have the listeners in the Utils method; better to move into the context.invokeEff
        // (or whatever the last mile before invoking the effector is - though currently there is not such a canonical place!)
        mgmtSupport.getEntityChangeListener().onEffectorStarting(eff, parameters);
        try {
            return mgmtContext.invokeEffector(entity, eff, parameters);
        } finally {
            // FIXME this is really Effector submitted
            mgmtSupport.getEntityChangeListener().onEffectorCompleted(eff);
        }
    }

    /** @deprecated since 0.7.0, not used */
    @Deprecated
    public static Effector<?> findEffectorMatching(Entity entity, Method method) {
        outer: for (Effector<?> effector : entity.getEntityType().getEffectors()) {
            if (!effector.getName().equals(entity)) continue;
            if (effector.getParameters().size() != method.getParameterTypes().length) continue;
            for (int i = 0; i < effector.getParameters().size(); i++) {
                if (effector.getParameters().get(i).getParameterClass() != method.getParameterTypes()[i]) continue outer;
            }
            return effector;
        }
        return null;
    }

    /** @deprecated since 0.7.0, expects parameters but does not use them! */
    @Deprecated
    public static Effector<?> findEffectorMatching(Set<Effector<?>> effectors, String effectorName, Map<String, ?> parameters) {
        // TODO Support overloading: check parameters as well
        for (Effector<?> effector : effectors) {
            if (effector.getName().equals(effectorName)) {
                return effector;
            }
        }
        return null;
    }

    /** matches effectors by name only (not parameters) */
    public static Maybe<Effector<?>> findEffector(Collection<? extends Effector<?>> effectors, String effectorName) {
        for (Effector<?> effector : effectors) {
            if (effector.getName().equals(effectorName)) {
                return Maybe.<Effector<?>>of(effector);
            }
        }
        return Maybe.absent(new NoSuchElementException("No effector with name "+effectorName+" (contenders "+effectors+")"));
    }

    /** matches effectors by name only (not parameters), based on what is declared on the entity static type */
    public static Maybe<Effector<?>> findEffectorDeclared(Entity entity, String effectorName) {
        return findEffector(entity.getEntityType().getEffectors(), effectorName);
    }

    /** @deprecated since 0.7.0 use {@link #getTaskFlagsForEffectorInvocation(Entity, Effector, ConfigBag)} */
    public static Map<Object,Object> getTaskFlagsForEffectorInvocation(Entity entity, Effector<?> effector) {
        return getTaskFlagsForEffectorInvocation(entity, effector, null);
    }
    
    /** returns a (mutable) map of the standard flags which should be placed on an effector */
    public static Map<Object,Object> getTaskFlagsForEffectorInvocation(Entity entity, Effector<?> effector, ConfigBag parameters) {
        List<Object> tags = MutableList.of(
                BrooklynTaskTags.EFFECTOR_TAG,
                BrooklynTaskTags.tagForEffectorCall(entity, effector.getName(), parameters),
                BrooklynTaskTags.tagForTargetEntity(entity));
        if (Entitlements.getEntitlementContext() != null) {
            tags.add(BrooklynTaskTags.tagForEntitlement(Entitlements.getEntitlementContext()));
        }
        return MutableMap.builder()
                .put("description", "Invoking effector "+effector.getName()
                    +" on "+entity.getDisplayName()
                    +(parameters!=null ? " with parameters "+parameters.getAllConfig() : ""))
                .put("displayName", effector.getName())
                .put("tags", tags)
                .build();
    }

}
