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
package brooklyn.policy;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.management.Task;

import com.google.common.collect.Maps;

/**
 * Gives details of an enricher to be created. It describes the enricher's configuration, and is
 * reusable to create multiple enrichers with the same configuration.
 * 
 * To create an EnricherSpec, it is strongly encouraged to use {@code create(...)} methods.
 * 
 * @param <T> The type of enricher to be created
 * 
 * @author aled
 */
public class EnricherSpec<T extends Enricher> extends AbstractBrooklynObjectSpec<T,EnricherSpec<T>> {

    private static final Logger log = LoggerFactory.getLogger(EnricherSpec.class);

    private final static long serialVersionUID = 1L;


    /**
     * Creates a new {@link EnricherSpec} instance for an enricher of the given type. The returned 
     * {@link EnricherSpec} can then be customized.
     * 
     * @param type A {@link Enricher} class
     */
    public static <T extends Enricher> EnricherSpec<T> create(Class<? extends T> type) {
        return new EnricherSpec<T>(type);
    }
    
    /**
     * Creates a new {@link EnricherSpec} instance with the given config, for an enricher of the given type.
     * 
     * This is primarily for groovy code; equivalent to {@code EnricherSpec.create(type).configure(config)}.
     * 
     * @param config The spec's configuration (see {@link EnricherSpec#configure(Map)}).
     * @param type   An {@link Enricher} class
     */
    public static <T extends Enricher> EnricherSpec<T> create(Map<?,?> config, Class<? extends T> type) {
        return EnricherSpec.create(type).configure(config);
    }
    
    private final Map<String, Object> flags = Maps.newLinkedHashMap();
    private final Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();

    protected EnricherSpec(Class<? extends T> type) {
        super(type);
    }
    
    protected void checkValidType(Class<? extends T> type) {
        checkIsImplementation(type, Enricher.class);
        checkIsNewStyleImplementation(type);
    }
    
    public EnricherSpec<T> uniqueTag(String uniqueTag) {
        flags.put("uniqueTag", uniqueTag);
        return this;
    }
    
    public EnricherSpec<T> configure(Map<?,?> val) {
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
        return this;
    }
    
    public EnricherSpec<T> configure(CharSequence key, Object val) {
        flags.put(checkNotNull(key, "key").toString(), val);
        return this;
    }
    
    public <V> EnricherSpec<T> configure(ConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> EnricherSpec<T> configureIfNotNull(ConfigKey<V> key, V val) {
        return (val != null) ? configure(key, val) : this;
    }

    public <V> EnricherSpec<T> configure(ConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> EnricherSpec<T> configure(HasConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    public <V> EnricherSpec<T> configure(HasConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    /**
     * @return Read-only construction flags
     * @see SetFromFlag declarations on the enricher type
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

    public abstract static class ExtensibleEnricherSpec<T extends Enricher,K extends ExtensibleEnricherSpec<T,K>> extends EnricherSpec<T> {
        private static final long serialVersionUID = -3649347642882809739L;
        
        protected ExtensibleEnricherSpec(Class<? extends T> type) {
            super(type);
        }

        @SuppressWarnings("unchecked")
        protected K self() {
            // we override the AbstractBrooklynObjectSpec method -- it's a different K here because
            // EnricherSpec does not contain a parametrisable generic return type (Self)
            return (K) this;
        }
        
        @Override
        public K uniqueTag(String uniqueTag) {
            super.uniqueTag(uniqueTag);
            return self();
        }

        @Override
        public K configure(Map<?, ?> val) {
            super.configure(val);
            return self();
        }

        @Override
        public K configure(CharSequence key, Object val) {
            super.configure(key, val);
            return self();
        }

        @Override
        public <V> K configure(ConfigKey<V> key, V val) {
            super.configure(key, val);
            return self();
        }

        @Override
        public <V> K configureIfNotNull(ConfigKey<V> key, V val) {
            super.configureIfNotNull(key, val);
            return self();
        }

        @Override
        public <V> K configure(ConfigKey<V> key, Task<? extends V> val) {
            super.configure(key, val);
            return self();
        }

        @Override
        public <V> K configure(HasConfigKey<V> key, V val) {
            super.configure(key, val);
            return self();
        }

        @Override
        public <V> K configure(HasConfigKey<V> key, Task<? extends V> val) {
            super.configure(key, val);
            return self();
        }
    }
}
