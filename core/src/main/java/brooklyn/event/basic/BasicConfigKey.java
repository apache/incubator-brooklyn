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
package brooklyn.event.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.guava.TypeTokens;
import brooklyn.util.internal.ConfigKeySelfExtracting;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

public class BasicConfigKey<T> implements ConfigKeySelfExtracting<T>, Serializable {
    
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BasicConfigKey.class);
    private static final long serialVersionUID = -1762014059150215376L;
    
    private static final Splitter dots = Splitter.on('.');

    @Beta
    public static <T> Builder<T> builder(TypeToken<T> type) {
        return new Builder<T>().type(type);
    }

    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<T>().type(type);
    }
    
    public static class Builder<T> {
        private String name;
        private TypeToken<T> type;
        private String description;
        private T defaultValue;
        private boolean reconfigurable;
        
        public Builder<T> name(String val) {
            this.name = val; return this;
        }
        public Builder<T> type(Class<T> val) {
            this.type = TypeToken.of(val); return this;
        }
        public Builder<T> type(TypeToken<T> val) {
            this.type = val; return this;
        }
        public Builder<T> description(String val) {
            this.description = val; return this;
        }
        public Builder<T> defaultValue(T val) {
            this.defaultValue = val; return this;
        }
        public Builder<T> reconfigurable(boolean val) {
            this.reconfigurable = val; return this;
        }
        public BasicConfigKey<T> build() {
            return new BasicConfigKey<T>(this);
        }
    }
    
    private String name;
    private TypeToken<T> typeToken;
    private Class<? super T> type;
    private String description;
    private T defaultValue;
    private boolean reconfigurable;

    // FIXME In groovy, fields were `public final` with a default constructor; do we need the gson?
    public BasicConfigKey() { /* for gson */ }

    public BasicConfigKey(Class<T> type, String name) {
        this(TypeToken.of(type), name);
    }

    public BasicConfigKey(Class<T> type, String name, String description) {
        this(TypeToken.of(type), name, description);
    }

    public BasicConfigKey(Class<T> type, String name, String description, T defaultValue) {
        this(TypeToken.of(type), name, description, defaultValue);
    }

    public BasicConfigKey(TypeToken<T> type, String name) {
        this(type, name, name, null);
    }
    
    public BasicConfigKey(TypeToken<T> type, String name, String description) {
        this(type, name, description, null);
    }
    
    public BasicConfigKey(TypeToken<T> type, String name, String description, T defaultValue) {
        this.description = description;
        this.name = checkNotNull(name, "name");
        
        this.type = TypeTokens.getRawTypeIfRaw(checkNotNull(type, "type"));
        this.typeToken = TypeTokens.getTypeTokenIfNotRaw(type);
        
        this.defaultValue = defaultValue;
        this.reconfigurable = false;
    }

    protected BasicConfigKey(Builder<T> builder) {
        this.name = checkNotNull(builder.name, "name");
        this.type = TypeTokens.getRawTypeIfRaw(checkNotNull(builder.type, "type"));
        this.typeToken = TypeTokens.getTypeTokenIfNotRaw(builder.type);
        this.description = builder.description;
        this.defaultValue = builder.defaultValue;
        this.reconfigurable = builder.reconfigurable;
    }
    
    /** @see ConfigKey#getName() */
    @Override public String getName() { return name; }

    /** @see ConfigKey#getTypeName() */
    @Override public String getTypeName() { return getType().getName(); }

    /** @see ConfigKey#getType() */
    @Override public Class<? super T> getType() { return TypeTokens.getRawType(typeToken, type); }

    /** @see ConfigKey#getTypeToken() */
    @Override public TypeToken<T> getTypeToken() { return TypeTokens.getTypeToken(typeToken, type); }
    
    /** @see ConfigKey#getDescription() */
    @Override public String getDescription() { return description; }

    /** @see ConfigKey#getDefaultValue() */
    @Override public T getDefaultValue() { return defaultValue; }

    /** @see ConfigKey#hasDefaultValue() */
    @Override public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    @Override
    public boolean isReconfigurable() {
        return reconfigurable;
    }
    
    /** @see ConfigKey#getNameParts() */
    @Override public Collection<String> getNameParts() {
        return Lists.newArrayList(dots.split(name));
    }
 
    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof BasicConfigKey)) return false;
        BasicConfigKey<?> o = (BasicConfigKey<?>) obj;
        
        return Objects.equal(name,  o.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
    
    @Override
    public String toString() {
        return String.format("%s[ConfigKey:%s]", name, getTypeName());
    }

    /**
     * Retrieves the value corresponding to this config key from the given map.
     * Could be overridden by more sophisticated config keys, such as MapConfigKey etc.
     */
    @SuppressWarnings("unchecked")
    @Override
    public T extractValue(Map<?,?> vals, ExecutionContext exec) {
        Object v = vals.get(this);
        try {
            return (T) resolveValue(v, exec);
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        return vals.containsKey(this);
    }
    
    protected Object resolveValue(Object v, ExecutionContext exec) throws ExecutionException, InterruptedException {
        return Tasks.resolveValue(v, getType(), exec, "config "+name);
    }

    /** used to record a key which overwrites another; only needed at disambiguation time 
     * if a class declares a key and an equivalent one (often inherited) which overwrites it.
     * See brooklyn.entity.basic.ConfigEntityInheritanceTest, and uses of this class, for more explanation.
     */
    public static class BasicConfigKeyOverwriting<T> extends BasicConfigKey<T> {
        private static final long serialVersionUID = -3458116971918128018L;

        private final ConfigKey<T> parentKey;
        
        public BasicConfigKeyOverwriting(ConfigKey<T> key, T defaultValue) {
            super(checkNotNull(key.getTypeToken(), "type"), checkNotNull(key.getName(), "name"), 
                    key.getDescription(), defaultValue);
            parentKey = key;
        }
        
        public ConfigKey<T> getParentKey() {
            return parentKey;
        }
    }
}
