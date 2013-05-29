package brooklyn.entity;

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.event.Sensor;

/**
 * Gives type information for an {@link Entity}. It is an immutable snapshot.
 * 
 * It reflects a given entity at the time the snapshot was created: if sensors
 * were added or removed on-the-fly then those changes will be included in subsequent
 * snapshots. Therefore instances of a given class of entity could have different 
 * EntityTypes.
 */
public interface EntityType extends Serializable {

    /**
     * The type name of this entity (normally the fully qualified class name).
     */
    String getName();
    
    /**
     * The simple type name of this entity (normally the unqualified class name).
     */
    String getSimpleName();

    /**
     * ConfigKeys available on this entity.
     */
    Set<ConfigKey<?>> getConfigKeys();
    
    /**
     * Sensors available on this entity.
     */
    Set<Sensor<?>> getSensors();
    
    /**
     * Effectors available on this entity.
     */
    Set<Effector<?>> getEffectors();

    /**
     * @return the matching effector on this entity
     * @throws NoSuchElementException If there is no exact match for this signature
     */
    Effector<?> getEffector(String name, Class<?>... parameterTypes);

    /**
     * The ConfigKey with the given name, or null if not found.
     */
    ConfigKey<?> getConfigKey(String name);
    
    /**
     * The Sensor with the given name, or null if not found.
     */
    Sensor<?> getSensor(String name);
    
    /**
     * @return True if has the sensor with the given name; false otherwise.
     */
    boolean hasSensor(String name);
}
