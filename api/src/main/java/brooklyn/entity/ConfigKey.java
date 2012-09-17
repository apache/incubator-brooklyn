package brooklyn.entity;

import java.util.Collection;

/**
 * Represents the name of a piece of typed configuration data for an entity.
 * <p>
 * Two ConfigKeys should be considered equal if they have the same FQN.
 */
public interface ConfigKey<T> {
    /**
     * Returns the description of the configuration parameter, for display.
     */
    String getDescription();

    /**
     * Returns the name of the configuration parameter, in a dot-separated namespace (FQN).
     */
    String getName();

    /**
     * Returns the constituent parts of the configuration parameter name as a {@link Collection}.
     */
    Collection<String> getNameParts();

    /**
     * Returns the type of the configuration parameter data.
     */
    Class<T> getType();

    /**
     * Returns the name of of the configuration parameter data type, as a {@link String}.
     */
    String getTypeName();

    /**
     * Returns the default value of the configuration parameter.
     */
    T getDefaultValue();

    /**
     * Returns true if a default configuration value has been set.
     */
    boolean hasDefaultValue();
    
    /** Interface for elements which want to be treated as a config key without actually being one
     * (e.g. config attribute sensors).
     */
    public interface HasConfigKey<T> {
        public ConfigKey<T> getConfigKey();
    }
}
