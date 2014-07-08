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
import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.internal.EffectorUtils;

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

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(EntityProxyImpl.class);

    private final Entity delegate;
    
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

    public EntityProxyImpl(Entity entity) {
        this.delegate = checkNotNull(entity, "entity");
    }
    
    @Override
    public String toString() {
        return delegate.toString();
    }
    
    public Object invoke(Object proxy, final Method m, final Object[] args) throws Throwable {
        if (proxy == null) {
            throw new IllegalArgumentException("Static methods not supported via proxy on entity "+delegate);
        }
        
        MethodSignature sig = new MethodSignature(m);

        Object result;
        if (OBJECT_METHODS.contains(sig)) {
            result = m.invoke(this, args);
        } else if (ENTITY_NON_EFFECTOR_METHODS.contains(sig)) {
            result = m.invoke(delegate, args);
        } else {
            Object[] nonNullArgs = (args == null) ? new Object[0] : args;
            Effector<?> eff = findEffector(m, nonNullArgs);
            if (eff != null) {
                result = EffectorUtils.invokeMethodEffector(delegate, eff, nonNullArgs);
            } else {
                result = m.invoke(delegate, nonNullArgs);
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
