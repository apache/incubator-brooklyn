package brooklyn.event.basic

import groovy.transform.EqualsAndHashCode

import java.util.Collection

import brooklyn.entity.ConfigKey

import com.google.common.base.Splitter
import com.google.common.collect.Lists

@EqualsAndHashCode(includeFields=true)
class BasicConfigKey<T> implements ConfigKey, Serializable {
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
}
