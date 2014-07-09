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
package io.brooklyn.camp.brooklyn.spi.creation;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Reflections;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/** Assists in loading types referenced from YAML;
 * mainly as a way to share logic used in very different contexts. */
public abstract class BrooklynYamlTypeInstantiator {

    private static final Logger log = LoggerFactory.getLogger(BrooklynYamlTypeInstantiator.class);
    
    protected final Factory factory;

    @Beta
    public static class Factory {
        final BrooklynClassLoadingContext loader;
        final Object contextForLogging;
        
        public Factory(BrooklynClassLoadingContext loader, Object contextForLogging) {
            this.loader = loader;
            this.contextForLogging = contextForLogging;
        }
        
        public InstantiatorFromKey from(Map<?,?> data) {
            return new InstantiatorFromKey(this, ConfigBag.newInstance(data));
        }
        
        public InstantiatorFromKey from(ConfigBag data) {
            return new InstantiatorFromKey(this, data);
        }
        
        public InstantiatorFromName type(String typeName) {
            return new InstantiatorFromName(this, typeName);
        }

    }
        
    public static class InstantiatorFromKey extends BrooklynYamlTypeInstantiator {
        protected final ConfigBag data;
        protected String typeKeyPrefix = null;
        
        /** Nullable only permitted for instances which do not do loading, e.g. LoaderFromKey#lookup */
        protected InstantiatorFromKey(@Nullable Factory factory, ConfigBag data) {
            super(factory);
            this.data = data;
        }
        
        public static Maybe<String> extractTypeName(String prefix, ConfigBag data) {
            if (data==null) return Maybe.absent();
            return new InstantiatorFromKey(null, data).prefix(prefix).getTypeName();
        }
        
        public InstantiatorFromKey prefix(String prefix) {
            typeKeyPrefix = prefix;
            return this;
        }

        public Maybe<String> getTypeName() {
            Maybe<Object> result = data.getStringKeyMaybe(getPreferredKeyName());
            if (result.isAbsent() && typeKeyPrefix!=null) {
                // try alternatives if a prefix was specified
                result = data.getStringKeyMaybe(typeKeyPrefix+"Type");
                if (result.isAbsent()) result = data.getStringKeyMaybe("type");
            }
            
            if (result.isAbsent()) return Maybe.absent("Missing key '"+getPreferredKeyName()+"'");
            
            if (result.get() instanceof String) return Maybe.of((String)result.get());
            
            throw new IllegalArgumentException("Invalid value "+result.get().getClass()+" for "+getPreferredKeyName()+"; "
                + "expected String, got "+result.get());
        }
        
        protected String getPreferredKeyName() {
            if (typeKeyPrefix!=null) return typeKeyPrefix+"_type";
            return "type";
        }
        
        /** as {@link #newInstance(Class)} but inferring the type */
        public Object newInstance() {
            return newInstance(null);
        }
        
        /** creates a new instance of the type referred to by this description,
         * as a subtype of the type supplied here, 
         * inferring a Map from <code>brooklyn.config</code> key.
         * TODO in future also picking up recognized flags and config keys (those declared on the type).  
         * <p>
         * constructs the object using:
         * <li> a constructor on the class taking a Map
         * <li> a no-arg constructor, only if the inferred map is empty  
         **/
        public <T> T newInstance(@Nullable Class<T> supertype) {
            Class<? extends T> type = getType(supertype);
            Map<String, ?> cfg = getConfigMap();
            Optional<? extends T> result = Reflections.invokeConstructorWithArgs(type, cfg);
            if (result.isPresent()) 
                return result.get();
            if (cfg.isEmpty()) {
                result = Reflections.invokeConstructorWithArgs(type);
                if (result.isPresent()) 
                    return result.get();
            }
            throw new IllegalStateException("No known mechanism for constructing type "+type+" in "+factory.contextForLogging);
        }

        /** finds the map of config for the type specified;
         * currently only gets <code>brooklyn.config</code>, returning empty map if none,
         * but TODO in future should support recognized flags and config keys (those declared on the type),
         * incorporating code in {@link BrooklynEntityMatcher}.
         */
        @SuppressWarnings("unchecked")
        @Nonnull
        public Map<String,?> getConfigMap() {
            MutableMap<String,Object> result = MutableMap.of();
            Object bc = data.getStringKey("brooklyn.config");
            if (bc!=null) {
                if (bc instanceof Map)
                    result.putAll((Map<? extends String, ?>) bc);
                else
                    throw new IllegalArgumentException("brooklyn.config key in "+factory.contextForLogging+" should be a map, not "+bc.getClass()+" ("+bc+")");
            }
            return result; 
        }

    }
    
    public static class InstantiatorFromName extends BrooklynYamlTypeInstantiator {
        protected final String typeName;
        protected InstantiatorFromName(Factory factory, String typeName) {
            super(factory);
            this.typeName = typeName;
        }
        
        public Maybe<String> getTypeName() {
            return Maybe.fromNullable(typeName);
        }
    }
    
    protected BrooklynYamlTypeInstantiator(Factory factory) {
        this.factory = factory;
    }
        
    public abstract Maybe<String> getTypeName();
    
    public BrooklynClassLoadingContext getClassLoadingContext() {
        Preconditions.checkNotNull("No factory set; cannot use this instance for type loading");
        return factory.loader;
    }
    
    public Class<?> getType() {
        return getType(Object.class);
    }
    
    public <T> Class<? extends T> getType(@Nonnull Class<T> type) {
        try {
            return getClassLoadingContext().loadClass(getTypeName().get(), type);
//            return loadClass(type, getTypeName().get(), factory.mgmt, factory.contextForLogging);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Unable to resolve " + type + " " + getTypeName().get() + " (rethrowing) in spec " + factory.contextForLogging);
            throw Exceptions.propagate(e);
        }
    }

}
