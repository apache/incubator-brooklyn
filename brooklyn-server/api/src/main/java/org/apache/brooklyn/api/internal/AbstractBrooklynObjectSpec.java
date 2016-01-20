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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.mgmt.EntityManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

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

    private static final Logger log = LoggerFactory.getLogger(AbstractBrooklynObjectSpec.class);
    
    private final Class<? extends T> type;
    private String displayName;
    private String catalogItemId;
    private Set<Object> tags = MutableSet.of();
    private List<SpecParameter<?>> parameters = ImmutableList.of();

    protected final Map<String, Object> flags = Maps.newLinkedHashMap();
    protected final Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();

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
        return Objects.toStringHelper(this).add("type", getType()).toString()+"@"+Integer.toHexString(System.identityHashCode(this));
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
    // TODO in many places (callers to this method) we prefer a wrapper item ID;
    // that is right, because the wrapper's defn will refer to the wrapped,
    // but we might need also to collect the item ID's so that *all* can be searched.
    // e.g. if R3 references R2 which references R1 any one of these might supply config keys 
    // referencing resources or types in their local bundles. 
    @Beta
    public SpecT catalogItemIdIfNotNull(String val) {
        if (val!=null) {
            catalogItemId = val;
        }
        return self();
    }

    
    public SpecT tag(Object tag) {
        tags.add(tag);
        return self();
    }

    /** adds the given tags */
    public SpecT tags(Iterable<Object> tagsToAdd) {
        return tagsAdd(tagsToAdd);
    }
    /** adds the given tags */
    public SpecT tagsAdd(Iterable<Object> tagsToAdd) {
        Iterables.addAll(this.tags, tagsToAdd);
        return self();
    }
    /** replaces tags with the given */
    public SpecT tagsReplace(Iterable<Object> tagsToReplace) {
        this.tags.clear();
        Iterables.addAll(this.tags, tagsToReplace);
        return self();
    }
    
    // TODO which semantics are correct? replace has been the behaviour;
    // add breaks tests and adds unwanted parameters,
    // but replacing will cause some desired parameters to be lost.
    // i (AH) think ideally the caller should remove any parameters which
    // have been defined as config keys, and then add the others;
    // or actually we should always add, since this is really defining the config keys,
    // and maybe extend the SpecParameter object to be able to advertise whether
    // it is a CatalogConfig or merely a config key, maybe introducing displayable, or even priority 
    // (but note part of the reason for CatalogConfig.priority is that java reflection doesn't preserve field order) .
    // see also comments on the camp SpecParameterResolver.
    @Beta
    public SpecT parameters(List<? extends SpecParameter<?>> parameters) {
        return parametersReplace(parameters);
    }
    /** adds the given parameters */
    @Beta
    public SpecT parametersAdd(List<? extends SpecParameter<?>> parameters) {
        // parameters follows immutable pattern, unlike the other fields
        Builder<SpecParameter<?>> result = ImmutableList.<SpecParameter<?>>builder();
        if (this.parameters!=null)
            result.addAll(this.parameters);
        result.addAll( checkNotNull(parameters, "parameters") );
        this.parameters = result.build();
        return self();
    }
    /** replaces parameters with the given */
    @Beta
    public SpecT parametersReplace(List<? extends SpecParameter<?>> parameters) {
        this.parameters = ImmutableList.copyOf( checkNotNull(parameters, "parameters") );
        return self();
    }

    /**
     * @return The type (often an interface) this spec represents and which will be instantiated from it 
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
        //Could be null after rebind
        if (parameters != null) {
            return ImmutableList.copyOf(parameters);
        } else {
            return ImmutableList.of();
        }
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
            .configure(otherSpec.getConfig())
            .configure(otherSpec.getFlags())
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
    public SpecT configure(Map<?,?> val) {
        for (Map.Entry<?, ?> entry: val.entrySet()) {
            if (entry.getKey()==null) throw new NullPointerException("Null key not permitted");
            if (entry.getKey() instanceof CharSequence)
                flags.put(entry.getKey().toString(), entry.getValue());
            else if (entry.getKey() instanceof ConfigKey<?>)
                config.put((ConfigKey<?>)entry.getKey(), entry.getValue());
            else if (entry.getKey() instanceof HasConfigKey<?>)
                config.put(((HasConfigKey<?>)entry.getKey()).getConfigKey(), entry.getValue());
            else {
                log.warn("Spec "+this+" ignoring unknown config key "+entry.getKey());
            }
        }
        return self();
    }

    public SpecT configure(CharSequence key, Object val) {
        flags.put(checkNotNull(key, "key").toString(), val);
        return self();
    }
    
    public <V> SpecT configure(ConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key"), val);
        return self();
    }

    public <V> SpecT configureIfNotNull(ConfigKey<V> key, V val) {
        return (val != null) ? configure(key, val) : self();
    }

    public <V> SpecT configure(ConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key"), val);
        return self();
    }

    public <V> SpecT configure(HasConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return self();
    }

    public <V> SpecT configure(HasConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return self();
    }

    public <V> SpecT removeConfig(ConfigKey<V> key) {
        config.remove( checkNotNull(key, "key") );
        return self();
    }

    /** Clears the config map, removing any config previously set. */
    public void clearConfig() {
        config.clear();
    }
        
    /**
     * @return Read-only construction flags
     * @see SetFromFlag declarations on the policy type
     */
    public Map<String, ?> getFlags() {
        return Collections.unmodifiableMap(flags);
    }
    
    /**
     * @return Read-only configuration values
     */
    public Map<ConfigKey<?>, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }

}
