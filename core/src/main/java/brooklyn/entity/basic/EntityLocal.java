package brooklyn.entity.basic;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.ConfigKey;
import brooklyn.management.ManagementContext;
import brooklyn.policy.Policy;

public interface EntityLocal extends Entity {
    /**
     * Gets the value of the given attribute on this entity, or null if has not been set.
     * 
     * Attributes can be things like workrate and status information, as well as 
     * configuration (e.g. url/jmxHost/jmxPort), etc.
     */
    <T> T getAttribute(AttributeSensor<T> sensor);

    /**
     * Update the {@link Sensor} data for the given attribute with a new value.
     * 
     * This can be used to "enrich" the entity, such as adding aggregated information, 
     * rolling averages, etc.
     * 
     * @return the old value for the attribute
     */
    <T> T updateAttribute(AttributeSensor<T> sensor, T val);
    
    // ??? = policy which detects a group is too hot and want the entity to fire a TOO_HOT event
    
    /**
     * Gets the given configuration value for this entity, which may be inherited from 
     * its owner.
     */
    <T> T getConfig(ConfigKey<T> key);
    
    /**
     * Must be called before the entity is started. Also must be called before the entity's 
     * "owned children" are added, to guarantee that those children inherit the config.
     */
    <T> T setConfig(ConfigKey<T> key, T val);
    
    /**
     * Emits a {@link SensorEvent} event on behalf of this entity (as though produced by this entity).
     */
    <T> void emit(Sensor<T> sensor, T value);
    
    /**
     * Allow us to subscribe to data from a {@link Sensor} on another entity.
     * 
     * @return a subscription id which can be used to unsubscribe
     */
    <T> long subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener);

    /**
     * @return an immutable thread-safe view of the policies.
     */
    Collection<Policy> getPolicies();
    
    /**
     * Adds the given policy to this entity. Also calls policy.setEntity if available.
     */
    void addPolicy(Policy policy);
    
    /**
     * Removes the given policy from this entity. 
     * @return True if the policy existed at this entity; false otherwise
     */
    boolean removePolicy(Policy policy);
    
    ManagementContext getManagementContext();
}
