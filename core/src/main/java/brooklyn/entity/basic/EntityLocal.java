package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;
import brooklyn.policy.basic.AbstractPolicy;

/** 
 * Extended Entity interface for use in places where the caller should have certain privileges,
 * such as setting attribute values, adding policies, etc.
 */
public interface EntityLocal extends Entity {
    
    // FIXME Rename to something other than EntityLocal.
    // Separate out what is specific to "local jvm", and what is here for an SPI rather than API.

    /**
     * Sets the entity's display name.
     */
    void setDisplayName(String displayName);

    /**
     * Sets the {@link Sensor} data for the given attribute to the specified value.
     * 
     * This can be used to "enrich" the entity, such as adding aggregated information, 
     * rolling averages, etc.
     * 
     * @return the old value for the attribute (possibly <code>null</code>)
     */
    <T> T setAttribute(AttributeSensor<T> sensor, T val);
    
    // ??? = policy which detects a group is too hot and want the entity to fire a TOO_HOT event
    
    <T> T getConfig(ConfigKey<T> key, T defaultValue);
    <T> T getConfig(HasConfigKey<T> key);
    <T> T getConfig(HasConfigKey<T> key, T defaultValue);
    
    /**
     * Must be called before the entity is started.
     */
    <T> T setConfig(ConfigKey<T> key, T val);
    <T> T setConfig(HasConfigKey<T> key, T val);
    
    /**
     * Emits a {@link SensorEvent} event on behalf of this entity (as though produced by this entity).
     * <p>
     * Note that for attribute sensors it is nearly always recommended to use setAttribute, 
     * as this method will not update local values.
     */
    <T> void emit(Sensor<T> sensor, T value);
    
    /**
     * Allow us to subscribe to data from a {@link Sensor} on another entity.
     * 
     * @return a subscription id which can be used to unsubscribe
     *
     * @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    // FIXME remove from interface?
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    // FIXME remove from interface?
    <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /**
     * Adds the given policy to this entity. Also calls policy.setEntity if available.
     */
    void addPolicy(AbstractPolicy policy);
    
    /**
     * Removes the given policy from this entity. 
     * @return True if the policy existed at this entity; false otherwise
     */
    boolean removePolicy(AbstractPolicy policy);
    
    /**
     * Removes all policy from this entity. 
     * @return True if any policies existed at this entity; false otherwise
     */
    boolean removeAllPolicies();
    
    /**
     * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
     */
    void addEnricher(AbstractEnricher enricher);
    
    /**
     * Removes the given enricher from this entity. 
     * @return True if the policy enricher at this entity; false otherwise
     */
    boolean removeEnricher(AbstractEnricher enricher);
    
    /**
     * Removes all enricher from this entity.
     * Use with caution as some entities automatically register enrichers; this will remove those enrichers as well.
     * @return True if any enrichers existed at this entity; false otherwise
     */
    boolean removeAllEnrichers();
    
    /** 
     * @return The management context for the entity, or null if it is not yet managed.
     */
    ManagementContext getManagementContext();
    
    /** 
     * @return The task execution context for the entity, or null if it is not yet managed.
     */    
    ExecutionContext getExecutionContext();
}
