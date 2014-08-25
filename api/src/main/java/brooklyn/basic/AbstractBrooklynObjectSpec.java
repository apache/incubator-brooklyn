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
package brooklyn.basic;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.Set;

import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public abstract class AbstractBrooklynObjectSpec<T,K extends AbstractBrooklynObjectSpec<T,K>> implements Serializable {

    private static final long serialVersionUID = 3010955277740333030L;
    
    private final Class<? extends T> type;
    private String displayName;
    private Set<Object> tags = MutableSet.of();

    protected AbstractBrooklynObjectSpec(Class<? extends T> type) {
        checkValidType(type);
        this.type = type;
    }
    
    @SuppressWarnings("unchecked")
    protected K self() {
        return (K) this;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", getType()).toString();
    }

    protected abstract void checkValidType(Class<? extends T> type);
    
    public K displayName(String val) {
        displayName = val;
        return self();
    }
    
    public K tag(Object tag) {
        tags.add(tag);
        return self();
    }

    /** adds the given tags */
    public K tags(Iterable<Object> tagsToAdd) {
        Iterables.addAll(this.tags, tagsToAdd);
        return self();
    }

    /**
     * @return The type of the object (or significant interface)
     */
    public Class<? extends T> getType() {
        return type;
    }
    
    /**
     * @return The display name of the object
     */
    public final String getDisplayName() {
        return displayName;
    }

    public final Set<Object> getTags() {
        return ImmutableSet.copyOf(tags);
    }

    // TODO Duplicates method in BasicEntityTypeRegistry and InternalEntityFactory.isNewStyleEntity
    protected final void checkIsNewStyleImplementation(Class<?> implClazz) {
        try {
            implClazz.getConstructor(new Class[0]);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Implementation "+implClazz+" must have a no-argument constructor");
        } catch (SecurityException e) {
            throw Exceptions.propagate(e);
        }
        
        if (implClazz.isInterface()) throw new IllegalStateException("Implementation "+implClazz+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(implClazz.getModifiers())) throw new IllegalStateException("Implementation "+implClazz+" is abstract, but must be a non-abstract class");
    }
    
    // TODO Duplicates method in BasicEntityTypeRegistry
    protected final void checkIsImplementation(Class<?> val, Class<? super T> requiredInterface) {
        if (!requiredInterface.isAssignableFrom(val)) throw new IllegalStateException("Implementation "+val+" does not implement "+requiredInterface.getName());
        if (val.isInterface()) throw new IllegalStateException("Implementation "+val+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(val.getModifiers())) throw new IllegalStateException("Implementation "+val+" is abstract, but must be a non-abstract class");
    }

}
