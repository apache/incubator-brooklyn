package io.brooklyn.camp.brooklyn.spi.creation;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Reflections;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public abstract class BrooklynYamlTypeLoader {

    private static final Logger log = LoggerFactory.getLogger(BrooklynYamlTypeLoader.class);
    
    protected final Factory factory;

    @Beta
    public static class Factory {
        final ManagementContext mgmt;
        final Object contextForLogging;
        
        public Factory(ManagementContext mgmt, Object contextForLogging) {
            this.mgmt = mgmt;
            this.contextForLogging = contextForLogging;
        }
        
        public LoaderFromKey from(Map<?,?> data) {
            return new LoaderFromKey(this, ConfigBag.newInstance(data));
        }
        
        public LoaderFromKey from(ConfigBag data) {
            return new LoaderFromKey(this, data);
        }
        
        public LoaderFromName type(String typeName) {
            return new LoaderFromName(this, typeName);
        }

    }
        
    public static class LoaderFromKey extends BrooklynYamlTypeLoader {
        protected final ConfigBag data;
        protected String typeKeyPrefix = null;
        
        /** Nullable only permitted for instances which do not do loading, e.g. LoaderFromKey#lookup */
        protected LoaderFromKey(@Nullable Factory factory, ConfigBag data) {
            super(factory);
            this.data = data;
        }
        
        public static Maybe<String> extractTypeName(String prefix, ConfigBag data) {
            if (data==null) return Maybe.absent();
            return new LoaderFromKey(null, data).prefix(prefix).getTypeName();
        }
        
        public LoaderFromKey prefix(String prefix) {
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
    
    public static class LoaderFromName extends BrooklynYamlTypeLoader {
        protected final String typeName;
        protected LoaderFromName(Factory factory, String typeName) {
            super(factory);
            this.typeName = typeName;
        }
        
        public Maybe<String> getTypeName() {
            return Maybe.fromNullable(typeName);
        }
    }
    
    protected BrooklynYamlTypeLoader(Factory factory) {
        this.factory = factory;
    }
        
    public abstract Maybe<String> getTypeName();
    
    public Class<?> getType() {
        return getType(null);
    }
    
    public <T> Class<? extends T> getType(@Nullable Class<T> type) {
        Preconditions.checkNotNull("No factory set; cannot use this instance for type loading");
        return loadClass(type, getTypeName().get(), factory.mgmt, factory.contextForLogging);
    }

    /** 
     * TODO in future will want OSGi-based resolver here (eg create from osgi:<bundle>: prefix
     * would use that OSGi mechanism here
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> loadClass(@Nullable Class<T> optionalSupertype, String typeName, ManagementContext mgmt, Object otherContext) {
        try {
            if (optionalSupertype!=null && Entity.class.isAssignableFrom(optionalSupertype)) 
                return (Class<T>) BrooklynEntityClassResolver.<Entity>resolveEntity(typeName, mgmt);
            else
                return BrooklynEntityClassResolver.<T>tryLoadFromClasspath(typeName, mgmt).get();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Unable to resolve "+typeName+" in spec "+otherContext);
            throw Exceptions.propagate(new IllegalStateException("Unable to resolve "
                + (optionalSupertype!=null ? optionalSupertype.getSimpleName()+" " : "")
                + "type '"+typeName+"'", e));
        }
    }


}
