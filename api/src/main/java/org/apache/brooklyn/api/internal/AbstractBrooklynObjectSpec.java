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
package org.apache.brooklyn.api.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.mgmt.EntityManager;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/** Defines a spec for creating a {@link BrooklynObject}.
 * <p>
 * In addition to the contract defined by the code,
 * subclasses should provide a public static <code>create(Class)</code>
 * method to create an instance of the spec for the target type indicated by the argument. 
 * <p>
 * The spec is then passed to type-specific methods,
 * e.g. {@link EntityManager#createEntity(org.apache.brooklyn.api.entity.EntitySpec)}
 * to create a managed instance of the target type. */
public abstract class AbstractBrooklynObjectSpec<T,SpecT extends AbstractBrooklynObjectSpec<T,SpecT>> implements Serializable {

    private static final long serialVersionUID = 3010955277740333030L;
    
    private final Class<? extends T> type;
    private String displayName;
    private String catalogItemId;
    private Set<Object> tags = MutableSet.of();
    private List<SpecParameter<?>> parameters = ImmutableList.of();

    protected AbstractBrooklynObjectSpec(Class<? extends T> type) {
        checkValidType(type);
        this.type = type;
        this.catalogItemId = ApiObjectsFactory.get().getCatalogItemIdFromContext();
    }
    
    @SuppressWarnings("unchecked")
    protected SpecT self() {
        return (SpecT) this;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", getType()).toString();
    }

    protected abstract void checkValidType(Class<? extends T> type);
    
    public SpecT displayName(String val) {
        displayName = val;
        return self();
    }
    
    public SpecT catalogItemId(String val) {
        catalogItemId = val;
        return self();
    }
    
    public SpecT tag(Object tag) {
        tags.add(tag);
        return self();
    }

    /** adds the given tags */
    public SpecT tags(Iterable<Object> tagsToAdd) {
        Iterables.addAll(this.tags, tagsToAdd);
        return self();
    }
    
    public SpecT parameters(List<? extends SpecParameter<?>> parameters) {
        this.parameters = ImmutableList.copyOf(checkNotNull(parameters, "parameters"));
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
    
    public final String getCatalogItemId() {
        return catalogItemId;
    }

    public final Set<Object> getTags() {
        return ImmutableSet.copyOf(tags);
    }

    /** A list of configuration options that the entity supports. */
    public final List<SpecParameter<?>> getParameters() {
        return ImmutableList.copyOf(parameters);
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
    
    protected SpecT copyFrom(SpecT otherSpec) {
        return displayName(otherSpec.getDisplayName())
            .tags(otherSpec.getTags())
            .catalogItemId(otherSpec.getCatalogItemId())
            .parameters(otherSpec.getParameters());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj==null) return false;
        if (!obj.getClass().equals(getClass())) return false;
        AbstractBrooklynObjectSpec<?,?> other = (AbstractBrooklynObjectSpec<?,?>)obj;
        if (!Objects.equal(getDisplayName(), other.getDisplayName())) return false;
        if (!Objects.equal(getCatalogItemId(), other.getCatalogItemId())) return false;
        if (!Objects.equal(getType(), other.getType())) return false;
        if (!Objects.equal(getTags(), other.getTags())) return false;
        if (!Objects.equal(getParameters(), other.getParameters())) return false;
        return true;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(getCatalogItemId(), getDisplayName(), getType(), getTags());
    }

    /** strings inserted as flags, config keys inserted as config keys; 
     * if you want to force one or the other, create a ConfigBag and convert to the appropriate map type */
    public abstract SpecT configure(Map<?,?> val);
    
}
