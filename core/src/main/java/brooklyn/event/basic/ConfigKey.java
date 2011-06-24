package brooklyn.event.basic;

import java.util.Collection;

public interface ConfigKey<T> {

    // TODO This is scarily similar looking to Sensor. But I think that's probably a good thing, with no shared super-type...
    
    // TODO 
    
    /**
     * Returns the description of the configuration parameter, for display.
     */
    public String getDescription();
 
    /**
     * Returns the name of the configuration parameter, in a dot-separated namespace.
     */
    public String getName();
 
    /**
     * Returns the constituent parts of the configuration parameter name as a {@link Collection}.
     */
    public Collection<String> getNameParts();
 
    /**
     * Returns the type of the configuration parameter data, as a {@link String} representation of the class name.
     */
    public String getType();
}
