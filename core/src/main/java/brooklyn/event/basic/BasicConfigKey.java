package brooklyn.event.basic;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.internal.ConfigKeySelfExtracting;
import brooklyn.util.task.Tasks;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

public class BasicConfigKey<T> implements ConfigKey<T>, ConfigKeySelfExtracting<T>, Serializable {
    private static final long serialVersionUID = -1762014059150215376L;
    
    private static final Splitter dots = Splitter.on('.');
    
    private String name;
    private Class<T> type;
    private String typeName;
    private String description;
    private T defaultValue;

    // FIXME In groovy, fields were `public final` with a default constructor; do we need the gson?
    public BasicConfigKey() { /* for gson */ }

    public BasicConfigKey(Class<T> type, String name) {
        this(type, name, name, null);
    }
    
    public BasicConfigKey(Class<T> type, String name, String description) {
        this(type, name, description, null);
    }
    
    public BasicConfigKey(Class<T> type, String name, String description, T defaultValue) {
        this.description = description;
        this.name = name;
        this.type = type;
        this.typeName = type.getName();
        this.defaultValue = defaultValue;
    }

    public BasicConfigKey(ConfigKey<T> key, T defaultValue) {
        this.description = key.getDescription();
        this.name = key.getName();
        this.type = key.getType();
        this.typeName = key.getTypeName();
        this.defaultValue = defaultValue;
    }
    
    /** @see ConfigKey#getName() */
    public String getName() { return name; }

    /** @see ConfigKey#getTypeName() */
    public String getTypeName() { return typeName; }

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
        return String.format("Config: %s (%s)", name, typeName);
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
}
