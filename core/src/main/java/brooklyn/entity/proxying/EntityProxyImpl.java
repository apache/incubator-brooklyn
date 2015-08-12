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

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.TaskAdaptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityTransientCopyInternal;
import brooklyn.entity.basic.EntityTransientCopyInternal.SpecialEntityTransientCopyInternal;
import brooklyn.entity.effector.EffectorWithBody;
import brooklyn.entity.rebind.RebindManagerImpl.RebindTracker;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.management.internal.EntityManagerInternal;
import brooklyn.management.internal.ManagementTransitionMode;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskTags;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;

/**
 * A dynamic proxy for an entity. Other entities etc should use these proxies when interacting
 * with the entity, rather than holding a reference to the specific object. That makes remoting
 * etc much simpler.
 * 
 * @author aled
 */
public class EntityProxyImpl implements java.lang.reflect.InvocationHandler {
    
    // TODO Currently the proxy references the real entity and invokes methods on it directly.
    // As we work on remoting/distribution, this will be replaced by RPC.

    private static final Logger LOG = LoggerFactory.getLogger(EntityProxyImpl.class);

    private Entity delegate;
    private Boolean isMaster;

    private WeakHashMap<Entity,Void> temporaryProxies = new WeakHashMap<Entity, Void>();
    
    private static final Set<MethodSignature> OBJECT_METHODS = Sets.newLinkedHashSet();
    static {
        for (Method m : Object.class.getMethods()) {
            OBJECT_METHODS.add(new MethodSignature(m));
        }
    }
    
    private static final Set<MethodSignature> ENTITY_NON_EFFECTOR_METHODS = Sets.newLinkedHashSet();
    static {
        for (Method m : Entity.class.getMethods()) {
            ENTITY_NON_EFFECTOR_METHODS.add(new MethodSignature(m));
        }
        for (Method m : EntityLocal.class.getMethods()) {
            ENTITY_NON_EFFECTOR_METHODS.add(new MethodSignature(m));
        }
        for (Method m : EntityInternal.class.getMethods()) {
            ENTITY_NON_EFFECTOR_METHODS.add(new MethodSignature(m));
        }
    }

    private static final Set<MethodSignature> ENTITY_PERMITTED_READ_ONLY_METHODS = Sets.newLinkedHashSet();
    static {
        for (Method m : EntityTransientCopyInternal.class.getMethods()) {
            ENTITY_PERMITTED_READ_ONLY_METHODS.add(new MethodSignature(m));
        }
        if (!ENTITY_NON_EFFECTOR_METHODS.containsAll(ENTITY_PERMITTED_READ_ONLY_METHODS)) {
            Set<MethodSignature> extras = new LinkedHashSet<EntityProxyImpl.MethodSignature>(ENTITY_PERMITTED_READ_ONLY_METHODS);
            extras.removeAll(ENTITY_NON_EFFECTOR_METHODS);
            throw new IllegalStateException("Entity read-only methods contains items not known as Entity methods: "+
                extras);
        }
        for (Method m : SpecialEntityTransientCopyInternal.class.getMethods()) {
            ENTITY_PERMITTED_READ_ONLY_METHODS.add(new MethodSignature(m));
        }
    }
    
    public EntityProxyImpl(Entity entity) {
        this.delegate = checkNotNull(entity, "entity");
    }
    
    /** invoked to specify that a different underlying delegate should be used, 
     * e.g. because we are switching copy impls or switching primary/copy*/
    public synchronized void resetDelegate(Entity thisProxy, Entity preferredProxy, Entity newDelegate) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("updating "+Integer.toHexString(System.identityHashCode(thisProxy))
                +" to be the same as "+Integer.toHexString(System.identityHashCode(preferredProxy))
                +" pointing at "+Integer.toHexString(System.identityHashCode(newDelegate)) 
                +" ("+temporaryProxies.size()+" temporary proxies)");
        }

        Entity oldDelegate = delegate;
        this.delegate = newDelegate;
        this.isMaster = null;
        
        if (newDelegate==oldDelegate)
            return;
        
        /* we have to make sure that any newly created proxy of the newDelegate 
         * which have leaked (eg by being set as a child) also get repointed to this new delegate */
        if (oldDelegate!=null) {
            Entity temporaryProxy = ((AbstractEntity)oldDelegate).getProxy();
            if (temporaryProxy!=null) temporaryProxies.put(temporaryProxy, null);
            ((AbstractEntity)oldDelegate).resetProxy(preferredProxy);
        }
        if (newDelegate!=null) {   
            Entity temporaryProxy = ((AbstractEntity)newDelegate).getProxy();
            if (temporaryProxy!=null) temporaryProxies.put(temporaryProxy, null);
            ((AbstractEntity)newDelegate).resetProxy(preferredProxy);
        }
        
        // update any proxies which might be in use
        for (Entity tp: temporaryProxies.keySet()) {
            if (tp==thisProxy || tp==preferredProxy) continue;
            ((EntityProxyImpl)(Proxy.getInvocationHandler(tp))).resetDelegate(tp, preferredProxy, newDelegate);
        }
    }
    
    @Override
    public String toString() {
        return delegate.toString();
    }
    
    protected boolean isMaster() {
        if (isMaster!=null) return isMaster;
        
        ManagementContext mgmt = ((EntityInternal)delegate).getManagementContext();
        ManagementTransitionMode mode = ((EntityManagerInternal)mgmt.getEntityManager()).getLastManagementTransitionMode(delegate.getId());
        Boolean ro = ((EntityInternal)delegate).getManagementSupport().isReadOnlyRaw();
        
        if (mode==null || ro==null) {
            // not configured yet
            return false;
        }
        boolean isMasterX = !mode.isReadOnly();
        if (isMasterX != !ro) {
            LOG.warn("Inconsistent read-only state for "+delegate+" (possibly rebinding); "
                + "management thinks "+isMasterX+" but entity thinks "+!ro);
            return false;
        }
        isMaster = isMasterX;
        return isMasterX;
    }
    
    public Object invoke(Object proxy, final Method m, final Object[] args) throws Throwable {
        if (proxy == null) {
            throw new IllegalArgumentException("Static methods not supported via proxy on entity "+delegate);
        }
        
        MethodSignature sig = new MethodSignature(m);

        Object result;
        if (OBJECT_METHODS.contains(sig)) {
            result = m.invoke(delegate, args);
        } else if (ENTITY_PERMITTED_READ_ONLY_METHODS.contains(sig)) {
            result = m.invoke(delegate, args);
        } else {
            if (!isMaster()) {
                if (isMaster==null || RebindTracker.isRebinding()) {
                    // rebinding or caller manipulating before management; permit all access
                    // (as of this writing, things seem to work fine without the isRebinding check;
                    // but including in it may allow us to tighten the methods in EntityTransientCopyInternal) 
                    result = m.invoke(delegate, args);
                } else {
                    throw new UnsupportedOperationException("Call to '"+sig+"' not permitted on read-only entity "+delegate);
                }
            } else if (ENTITY_NON_EFFECTOR_METHODS.contains(sig)) {
                result = m.invoke(delegate, args);
            } else {
                Object[] nonNullArgs = (args == null) ? new Object[0] : args;
                Effector<?> eff = findEffector(m, nonNullArgs);
                if (eff != null) {
                    @SuppressWarnings("rawtypes")
                    Map parameters = EffectorUtils.prepareArgsForEffectorAsMapFromArray(eff, nonNullArgs);
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    TaskAdaptable<?> task = ((EffectorWithBody)eff).getBody().newTask(delegate, eff, ConfigBag.newInstance(parameters));
                    // as per LocalManagementContext.runAtEntity(Entity entity, TaskAdaptable<T> task) 
                    TaskTags.markInessential(task);
                    result = DynamicTasks.queueIfPossible(task.asTask()).orSubmitAsync(delegate).andWaitForSuccess();
                } else {
                    result = m.invoke(delegate, nonNullArgs);
                }
            }
        }
        
        return (result == delegate && delegate instanceof AbstractEntity) ? ((AbstractEntity)result).getProxy() : result;
    }
    
    private Effector<?> findEffector(Method m, Object[] args) {
        String name = m.getName();
        Set<Effector<?>> effectors = delegate.getEntityType().getEffectors();
        for (Effector<?> contender : effectors) {
            if (name.equals(contender.getName())) {
                return contender;
            }
        }
        return null;
    }
    
    private static class MethodSignature {
        private final String name;
        private final Class<?>[] parameterTypes;
        
        MethodSignature(Method m) {
            name = m.getName();
            parameterTypes = m.getParameterTypes();
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(name, Arrays.hashCode(parameterTypes));
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MethodSignature)) return false;
            MethodSignature o = (MethodSignature) obj;
            return name.equals(o.name) && Arrays.equals(parameterTypes, o.parameterTypes);
        }
        
        @Override
        public String toString() {
            return name+Arrays.toString(parameterTypes);
        }
    }
    
    @VisibleForTesting
    public Entity getDelegate() {
        return delegate;
    }
    
    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }
    
    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
