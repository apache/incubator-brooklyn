package brooklyn.config;

import java.util.Collection;

import com.google.common.reflect.TypeToken;

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
     * Returns the Guava TypeToken, including info on generics.
     */
    TypeToken<T> getTypeToken();
    
    /**
     * Returns the type of the configuration parameter data.
     * <p> 
     * This returns a "super" of T only in the case where T is generified, 
     * and in such cases it returns the Class instance for the unadorned T ---
     * i.e. for List<String> this returns Class<List> ---
     * this is of course because there is no actual Class<List<String>> instance.
     */
    Class<? super T> getType();

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
    
    /**
     * @return True if the configuration can be changed at runtime.
     */
    boolean isReconfigurable();

    /** Interface for elements which want to be treated as a config key without actually being one
     * (e.g. config attribute sensors).
     */
    public interface HasConfigKey<T> {
        public ConfigKey<T> getConfigKey();
    }
}
