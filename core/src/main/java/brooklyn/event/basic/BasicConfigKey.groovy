package brooklyn.event.basic

import groovy.transform.EqualsAndHashCode

import java.util.Collection
import java.util.Map
import java.util.concurrent.Future

import brooklyn.entity.ConfigKey
import brooklyn.management.ExecutionContext
import brooklyn.management.Task
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ConfigKeySelfExtracting
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Splitter
import com.google.common.collect.Lists

@EqualsAndHashCode(includeFields=true)
class BasicConfigKey<T> implements ConfigKey<T>, ConfigKeySelfExtracting<T>, Serializable {
    private static final long serialVersionUID = -1762014059150215376L
    
    private static final Splitter dots = Splitter.on('.')
    
    public final String name
    public final Class<T> type
    public final String typeName
    public final String description
    public final T defaultValue
    
    public BasicConfigKey() { /* for gson */ }

    public BasicConfigKey(Class<T> type, String name, String description=name, T defaultValue=null) {
        this.description = description
        this.name = name
        this.type = type
        this.typeName = type.name
        this.defaultValue = defaultValue
    }

    public BasicConfigKey(ConfigKey key, T defaultValue) {
        this.description = key.description
        this.name = key.name
        this.type = key.type
        this.typeName = key.typeName
        this.defaultValue = defaultValue
    }
    
    /** @see ConfigKey#getName() */
    public String getName() { name }

    /** @see ConfigKey#getTypeName() */
    public String getTypeName() { typeName }

    /** @see ConfigKey#getType() */
    public Class<T> getType() { type }

    /** @see ConfigKey#getDescription() */
    public String getDescription() { description }

    /** @see ConfigKey#getDefaultValue() */
    public T getDefaultValue() { defaultValue }

    /** @see ConfigKey#hasDefaultValue() */
    public boolean hasDefaultValue() {
        return defaultValue != null
    }

    /** @see ConfigKey#getNameParts() */
    public Collection<String> getNameParts() {
        return Lists.newArrayList(dots.split(name));
    }
 
    @Override
    public String toString() {
        return String.format("Config: %s (%s)", name, typeName)
    }

    /**
     * Retrieves the value corresponding to this config key from the given map.
     * Could be overridden by more sophisticated config keys, such as MapConfigKey etc.
     */
    @Override
    public T extractValue(Map vals, ExecutionContext exec) {
        Object v = vals.get(this)
        return resolveValue(v, exec)
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        return vals.containsKey(this)
    }
    
    protected Object resolveValue(Object v, ExecutionContext exec) {
        //if the expected type is a closure or map and that's what we have, we're done (or if it's null)
        if (v==null || type.isInstance(v))
            return v;
        try {
            //if it's a task, we wait for the task to complete
            if (v in Task) {
                if (!((Task) v).isSubmitted() ) {
                    exec.submit((Task) v)
                }
                v = ((Task) v).get()
            } else if (v in Future) {
                v = ((Future) v).get()
            } else if (v in Closure) {
                v = ((Closure) v).call()
            } else if (v in Map) {
                //and if a map or list we look inside
                Map result = [:]
                v.each { k,val -> result << [(k): resolveValue(val, exec)] }
                return result
            } else if (v in List) {
                List result = []
                v.each { result << resolveValue(it, exec) }
                return result
            } else {
                return TypeCoercions.coerce(v, type);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error resolving "+v+" for "+this+" in "+exec+": "+e, e);
        }
        return resolveValue(v, exec)
    }
}

class SubElementConfigKey<T> extends BasicConfigKey<T> {
    public final ConfigKey parent
    
    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name, String description=name, T defaultValue=null) {
        super(type, name, description, defaultValue)
        this.parent = parent
    }
    
    @Override
    public T extractValue(Map vals, ExecutionContext exec) {
        return super.extractValue(vals, exec)
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        return super.isSet(vals)
    }
}

// TODO Create interface
class MapConfigKey<V> extends BasicConfigKey<Map<String,V>> {
    public final Class<V> subType
    
    public MapConfigKey(Class<V> subType, String name, String description=name, Map<String,V> defaultValue=null) {
        super(Map.class, name, description, defaultValue)
        this.subType = subType
    }

    public ConfigKey<V> subKey(String subName) {
        new SubElementConfigKey(this, subType, name+"."+subName, "sub-element of "+name+", named "+subName, null)
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this == ((SubElementConfigKey)contender).parent)
    }
    
    public String extractSubKeyName(ConfigKey<?> subKey) {
        return subKey.name.substring(name.length()+1)
    }
    
    @Override
    public Map<String,V> extractValue(Map vals, ExecutionContext exec) {
        Map<String,V> result = [:]
        vals.each { k,v -> 
            if (isSubKey(k)) {
                result.put(extractSubKeyName(k), k.extractValue(vals, exec))
            }
        }
        return result
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        if (vals.containsKey(this)) return true
        for (ConfigKey contender in vals.keySet()) {
            if (isSubKey(contender)) {
                return true
            }
        }
        return false
    }
}

// TODO Create interface
class ListConfigKey<V> extends BasicConfigKey<List<V>> {
    public final Class<V> subType
    
    public ListConfigKey(Class<V> subType, String name, String description=name, Map<String,V> defaultValue=null) {
        super(List.class, name, description, defaultValue)
        this.subType = subType
    }

    public ConfigKey<V> subKey() {
        String subName = LanguageUtils.newUid()
        new SubElementConfigKey(this, subType, name+"."+subName, "element of "+name+", uid "+subName, null)
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this == ((SubElementConfigKey)contender).parent)
    }
    
    public List<V> extractValue(Map vals, ExecutionContext exec) {
        List<V> result = []
		vals.each { k,v -> 
			if (isSubKey(k))
				result << ((SubElementConfigKey)k).extractValue(vals, exec)
        }
        return result
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        if (vals.containsKey(this)) return true
        for (ConfigKey contender in vals.keySet()) {
            if (isSubKey(contender)) {
                return true
            }
        }
        return false
    }
}
