package brooklyn.event.basic;

import groovy.lang.Closure;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import brooklyn.entity.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ConfigKeySelfExtracting;
import brooklyn.util.task.BasicExecutionManager;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
        
        return Objects.equal(name,  o.name) && Objects.equal(typeName,  o.typeName) && 
                Objects.equal(description,  o.description) && Objects.equal(defaultValue,  o.defaultValue);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(name, type, typeName, description, defaultValue);
    }
    
    @Override
    public String toString() {
        return String.format("Config: %s (%s)", name, typeName);
    }

    /**
     * Retrieves the value corresponding to this config key from the given map.
     * Could be overridden by more sophisticated config keys, such as MapConfigKey etc.
     */
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
        return resolveValue(v, type, exec);
    }
    /** attempt to resolve the given value as the given type, waiting on futures,
     * and coercing as allowed by TypeCoercions */
    public static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec) throws ExecutionException, InterruptedException {
        //if the expected type is a closure or map and that's what we have, we're done (or if it's null);
        //but not allowed to return a future as the resolved value
        if (v==null || (type.isInstance(v) && !Future.class.isInstance(v)))
            return (T) v;
        try {
            //if it's a task or a future, we wait for the task to complete
            if (v instanceof Task) {
                //if it's a task or a future, we make sure it is submitted
                //(perhaps could run it here? ... tbd)
                if (!((Task) v).isSubmitted() ) {
                    exec.submit((Task) v);
                }
            }
            if (v instanceof Future) {
                final Future<?> vfuture = (Future<?>) v;
                
                //including tasks, above
                if (!vfuture.isDone()) {
                    final AtomicReference<Object> vref = new AtomicReference<Object>(v);
                    
                    BasicExecutionManager.withBlockingDetails("waiting for "+v, new Callable<Void>() {
                        public Void call() throws Exception {
                            vref.set( vfuture.get() );
                            return null;
                        }
                    });
                    
                    v = vref.get();
                    
                } else {
                    v = vfuture.get();
                }
            } else if (v instanceof Closure) {
                v = ((Closure) v).call();
            } else if (v instanceof Map) {
                //and if a map or list we look inside
                Map result = Maps.newLinkedHashMap();
                for (Map.Entry<?,?> entry : ((Map<?,?>)v).entrySet()) {
                    result.put(entry.getKey(), resolveValue(entry.getValue(), type, exec));
                }
                return (T) result;
            } else if (v instanceof List) {
                List result = Lists.newArrayList();
                for (Object it : (List)v) {
                    result.add(resolveValue(it, type, exec));
                }
                return (T) result;
            } else {
                return TypeCoercions.coerce(v, type);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error resolving "+v+" in "+exec+": "+e, e);
        }
        return resolveValue(v, type, exec);
    }
}
