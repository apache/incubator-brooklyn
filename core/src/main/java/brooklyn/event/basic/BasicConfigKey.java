package brooklyn.event.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.internal.ConfigKeySelfExtracting;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

public class BasicConfigKey<T> implements ConfigKeySelfExtracting<T>, Serializable {
    private static final long serialVersionUID = -1762014059150215376L;
    
    private static final Splitter dots = Splitter.on('.');

    // TODO For use with generics; TODO accept some form of ParameterizedType
    @Beta
    public static <T> Builder<T> builder(TypeToken<T> type) {
        return new Builder<T>().type(type);
    }

    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<T>().type(type);
    }
    
    public static class Builder<T> {
        private String name;
        private Class<T> type;
        private String description;
        private T defaultValue;
        private boolean reconfigurable;
        
        public Builder<T> name(String val) {
            this.name = val; return this;
        }
        public Builder<T> type(Class<T> val) {
            this.type = val; return this;
        }
        @SuppressWarnings("unchecked")
        public Builder<T> type(TypeToken<T> val) {
            this.type = (Class<T>) val.getRawType(); return this;
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
    private Class<T> type;
    private String description;
    private T defaultValue;
    private boolean reconfigurable;

    // FIXME In groovy, fields were `public final` with a default constructor; do we need the gson?
    public BasicConfigKey() { /* for gson */ }

    // TODO How to do this without cast; the but T in TypeToken could be a ParameterizedType 
    // so it really could be a super-type of T rather than Class<T>!
    @SuppressWarnings("unchecked")
    public BasicConfigKey(TypeToken<T> type, String name) {
        this((Class<T>) type.getRawType(), name);
    }

    @SuppressWarnings("unchecked")
    public BasicConfigKey(TypeToken<T> type, String name, String description) {
        this((Class<T>) type.getRawType(), name, description);
    }

    @SuppressWarnings("unchecked")
    public BasicConfigKey(TypeToken<T> type, String name, String description, T defaultValue) {
        this((Class<T>) type.getRawType(), name, description, defaultValue);
    }

    public BasicConfigKey(Class<T> type, String name) {
        this(type, name, name, null);
    }
    
    public BasicConfigKey(Class<T> type, String name, String description) {
        this(type, name, description, null);
    }
    
    public BasicConfigKey(Class<T> type, String name, String description, T defaultValue) {
        this.description = description;
        this.name = checkNotNull(name, "name");
        this.type = checkNotNull(type, "type");
        this.defaultValue = defaultValue;
        this.reconfigurable = false;
    }

    public BasicConfigKey(ConfigKey<T> key, T defaultValue) {
        this.description = key.getDescription();
        this.name = checkNotNull(key.getName(), "name");
        this.type = checkNotNull(key.getType(), "type");
        this.defaultValue = defaultValue;
        this.reconfigurable = false;
    }

    protected BasicConfigKey(Builder<T> builder) {
        this.name = checkNotNull(builder.name, "name");
        this.type = checkNotNull(builder.type, "type");
        this.description = builder.description;
        this.defaultValue = builder.defaultValue;
        this.reconfigurable = builder.reconfigurable;
    }
    
    /** @see ConfigKey#getName() */
    public String getName() { return name; }

    /** @see ConfigKey#getTypeName() */
    public String getTypeName() { return type.getName(); }

    /** @see ConfigKey#getType() */
    public Class<T> getType() { return type; }

    /** @see ConfigKey#getDescription() */
    public String getDescription() { return description; }

    /** @see ConfigKey#getDefaultValue() */
    public T getDefaultValue() { return defaultValue; }

    /** @see ConfigKey#hasDefaultValue() */
    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    @Override
    public boolean isReconfigurable() {
        return reconfigurable;
    }
    
    /** @see ConfigKey#getNameParts() */
    public Collection<String> getNameParts() {
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
        return Tasks.resolveValue(v, type, exec, "config "+name);
    }
    
    /** attempt to resolve the given value as the given type, waiting on futures,
     * and coercing as allowed by TypeCoercions 
     * @deprecated in 0.4.0, use Tasks.resolveValue */
    public static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec) throws ExecutionException, InterruptedException {
        return Tasks.resolveValue(v, type, exec);
    }
    
    public static class StringConfigKey extends BasicConfigKey<String> {
        private static final long serialVersionUID = 8207099275514012088L;
        
        public StringConfigKey(String name) {
            super(String.class, name);
        }
        public StringConfigKey(String name, String description, String defaultValue) {
            super(String.class, name, description, defaultValue);
        }
        public StringConfigKey(ConfigKey<String> orig, String defaultValue) {
            super(orig, defaultValue);
        }
    }
    
    public static class BooleanConfigKey extends BasicConfigKey<Boolean> {
        private static final long serialVersionUID = 3207099275514012099L;

        public BooleanConfigKey(String name) {
            super(Boolean.class, name);
        }
        public BooleanConfigKey(String name, String description, Boolean defaultValue) {
            super(Boolean.class, name, description, defaultValue);
        }
        public BooleanConfigKey(ConfigKey<Boolean> orig, Boolean defaultValue) {
            super(orig, defaultValue);
        }
    }
    
}
