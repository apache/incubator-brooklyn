package brooklyn.entity;

import java.util.Collection;

/**
 * Represents the name of a piece of typed configuration data for an entity.
 */
public interface ConfigKey<T> {
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
