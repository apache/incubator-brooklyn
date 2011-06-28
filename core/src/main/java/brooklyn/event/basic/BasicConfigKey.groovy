package brooklyn.event.basic

import groovy.transform.EqualsAndHashCode

import java.util.Collection

import com.google.common.base.Splitter
import com.google.common.collect.Lists

@EqualsAndHashCode(includeFields=true)
class BasicConfigKey<T> implements ConfigKey, Serializable {

    private static final long serialVersionUID = -1762014059150215376L;
    
    private static final Splitter dots = Splitter.on('.');
    
    public final String name;
    //TODO Alex strongly suggests: make it a Class. java API nicer that way. use custom serialisers rather than corrupt the internal type
    //or if you must, make it transient, and have non-transient String typeName, and getType will check for type being null...
    public final String type;
    public final String description;
    
    public BasicConfigKey() { /* for gson */ }
    
    public BasicConfigKey(String name, Class<T> type, String description=name) {
        this.description = description;
        this.name = name;
        this.type = type.getName();
    }

    @Override
    public String getName() {
        return name;
    }
    
    String getType() {
        return type;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    /** @see Sensor#getNameParts() */
    public Collection<String> getNameParts() {
        return Lists.newArrayList(dots.split(name));
    }
    
    public String toString() {
        return "Config:"+name;
    }
}
