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

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;

public class BasicEntityTypeRegistry implements EntityTypeRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(BasicEntityTypeRegistry.class);

    private final BiMap<Class<?>, Class<?>> registry = HashBiMap.create();
    private final BiMap<Class<?>, Class<?>> cache = HashBiMap.create();
    
    private final Object mutex = new Object();
    
    @Override
    public <T extends Entity> EntityTypeRegistry registerImplementation(Class<T> type, Class<? extends T> implClazz) {
        checkNotNull(type, "type");
        checkNotNull(implClazz, "implClazz");
        checkIsImplementation(type, implClazz);
        checkIsNewStyleImplementation(implClazz);

        synchronized (mutex) {
            Class<?> existingType = registry.inverse().get(implClazz);
            if (existingType != null && !type.equals(existingType)) {
                throw new IllegalArgumentException("Implementation "+implClazz+" already registered against type "+existingType+"; cannot also register against "+type);
            }
            
            LOG.debug("Implementation {} registered against type {}", implClazz, type);
            registry.put(type, implClazz);
            cache.forcePut(type, implClazz);
        }
        
        return this;
    }
    
    @Override
    public <T extends Entity> Class<? extends T> getImplementedBy(Class<T> type) {
        synchronized (mutex) {
            Class<?> result = cache.get(type);
            if (result != null) {
                if (LOG.isTraceEnabled()) LOG.trace("Implementation {} returned for type {}", result, type);
                return (Class<? extends T>) result;
            }
            result = getFromAnnotation(type);
            if (result == null) {
                if (!type.isInterface() && ((type.getModifiers() & Modifier.ABSTRACT)==0)) {
                    // warning delivered later, in InternalEntityFactory
                    result = type;
                } else {
                    throw new IllegalArgumentException("Interface "+type+" is not annotated with @"+ImplementedBy.class.getSimpleName()+", and no implementation is registered");
                }
            }
            if (LOG.isTraceEnabled()) LOG.trace("Implementation {} returned for type {}", result, type);
            cache.put(type, result);
            return (Class<? extends T>) result;
        }
    }

    @Override
    public <T extends Entity> Class<? super T> getEntityTypeOf(Class<T> implClazz) {
        synchronized (mutex) {
            Class<?> result = cache.inverse().get(implClazz);
            if (result != null) {
                return (Class<? super T>) result;
            }
    
            result = getInterfaceWithAnnotationMatching(implClazz);
            cache.put(implClazz, result);
            return (Class<? super T>) result;
        }
    }

    private <T extends Entity> Class<? extends T> getFromAnnotation(Class<T> type) {
        ImplementedBy annotation = type.getAnnotation(brooklyn.entity.proxying.ImplementedBy.class);
        if (annotation == null) 
            return null;
        Class<? extends Entity> value = annotation.value();
        checkIsImplementation(type, value);
        return (Class<? extends T>) value;
    }

    private <T extends Entity> Class<? super T> getInterfaceWithAnnotationMatching(Class<T> implClazz) {
        // getInterfaces() only looks at one level of interfaces (i.e. not interfaces declared on supertypes)
        // so if an impl indirectly extends the interface we need to look deeper.
        // -- see also Reflections.getInterfacesIncludingClassAncestors and usages of that (duplication?)
        Set<Class<?>> visited = Sets.newLinkedHashSet();
        Deque<Class<?>> tovisit = new LinkedList<Class<?>>();
        tovisit.add(implClazz);
        
        while (!tovisit.isEmpty()) {
            Class<?> contender = tovisit.pop();
            if (contender == null || visited.contains(contender)) continue;
            visited.add(contender);
            
            if (contender.isInterface()) {
                ImplementedBy annotation = contender.getAnnotation(brooklyn.entity.proxying.ImplementedBy.class);
                Class<? extends Entity> value = (annotation == null) ? null : annotation.value();
                if (implClazz.equals(value)) return (Class<? super T>) contender;
            }
            
            tovisit.addAll(Arrays.asList(contender.getInterfaces()));
            tovisit.add(contender.getSuperclass());
        }
        throw new IllegalArgumentException("Interfaces of "+implClazz+" not annotated with @"+ImplementedBy.class.getSimpleName()+" matching this class");
    }
    
    private void checkIsImplementation(Class<?> type, Class<?> implClazz) {
        if (!type.isAssignableFrom(implClazz)) throw new IllegalStateException("Implementation "+implClazz+" does not implement "+type);
        if (implClazz.isInterface()) throw new IllegalStateException("Implementation "+implClazz+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(implClazz.getModifiers())) throw new IllegalStateException("Implementation "+implClazz+" is abstract, but must be a non-abstract class");
    }
    
    private void checkIsNewStyleImplementation(Class<?> implClazz) {
        try {
            implClazz.getConstructor(new Class[0]);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Implementation "+implClazz+" must have a no-argument constructor");
        } catch (SecurityException e) {
            throw Exceptions.propagate(e);
        }
    }
}
