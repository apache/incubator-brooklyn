package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;
import brooklyn.management.Task;

import com.google.common.annotations.Beta;

/** 
 * Extended Entity interface for use in places where the caller should have certain privileges,
 * such as setting attribute values, adding policies, etc.
 */
public interface EntityLocal extends Entity {
    
    // FIXME Rename to something other than EntityLocal.
    // Separate out what is specific to "local jvm", and what is here for an SPI rather than API.

    /**
     * Sets the entity's display name.
     * Must be called before the entity is managed.
     */
    void setDisplayName(String displayName);

    /**
     * Must be called before the entity is managed.
     */
    <T> T setConfig(ConfigKey<T> key, T val);
    <T> T setConfig(ConfigKey<T> key, Task<T> val);
    <T> T setConfig(HasConfigKey<T> key, T val);
    <T> T setConfig(HasConfigKey<T> key, Task<T> val);

    /**
     * Sets the {@link Sensor} data for the given attribute to the specified value.
     * 
     * This can be used to "enrich" the entity, such as adding aggregated information, 
     * rolling averages, etc.
     * 
     * @return the old value for the attribute (possibly {@code null})
     */
    <T> T setAttribute(AttributeSensor<T> sensor, T val);

    /** sets the value of the given attribute sensor from the config key value herein,
     * if the config key resolves to a non-null value as a sensor
     * 
     * @deprecated since 0.5; use {@link #setAttribute(AttributeSensor, Object)}, such as 
     * <pre>
     * T val = getConfig(KEY.getConfigKey());
     * if (val != null) {
     *     setAttribute(KEY, val)
     * }
     * </pre>
     * 
     * @return old value
     */
    <T> T setAttribute(AttributeSensorAndConfigKey<?,T> configuredSensor);

    /**
     * @deprecated in 0.5; use {@link #getConfig(ConfigKey)}
     */
    <T> T getConfig(ConfigKey<T> key, T defaultValue);
    
    <T> T getConfig(HasConfigKey<T> key);
    
    /**
     * @deprecated in 0.5; use {@link #getConfig(HasConfigKey)}
     */
    <T> T getConfig(HasConfigKey<T> key, T defaultValue);

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
    @Beta
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    // FIXME remove from interface?
    @Beta
    <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see SubscriptionManager#subscribeToMembers(Group, Sensor, SensorEventListener) */
    // FIXME remove from interface?
    @Beta
    <T> SubscriptionHandle subscribeToMembers(Group group, Sensor<T> sensor, SensorEventListener<? super T> listener);

    /**
     * Unsubscribes from the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    @Beta
    boolean unsubscribe(Entity producer);

    /**
     * Unsubscribes the given handle.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    @Beta
    boolean unsubscribe(Entity producer, SubscriptionHandle handle);

    /**
     * Removes all policy from this entity. 
     * @return True if any policies existed at this entity; false otherwise
     */
    boolean removeAllPolicies();
    
    /**
     * Removes all enricher from this entity.
     * Use with caution as some entities automatically register enrichers; this will remove those enrichers as well.
     * @return True if any enrichers existed at this entity; false otherwise
     */
    boolean removeAllEnrichers();
    
    /** 
     * @return The management context for the entity, or null if it is not yet managed.
     * @deprecated since 0.4.0 access via getManagementSupport
     * @see EntityInternal.getManagementContext()
     */
    ManagementContext getManagementContext();

    /** 
     * @return The task execution context for the entity, or null if it is not yet managed.
     * @deprecated since 0.4.0 access via getManagementSupport
     * @see EntityInternal.getExecutionContext()
     */    
    ExecutionContext getExecutionContext();
}
