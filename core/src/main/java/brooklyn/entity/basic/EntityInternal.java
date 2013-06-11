package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.location.Location;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.internal.EntityManagementSupport;

import com.google.common.annotations.Beta;

/** 
 * Extended Entity interface with additional functionality that is purely-internal (i.e. intended 
 * for the brooklyn framework only).
 */
@Beta
public interface EntityInternal extends Entity {
    
    void addLocations(Collection<? extends Location> locations);

    void removeLocations(Collection<? extends Location> locations);

    void clearLocations();

    /**
     * 
     * Like {@link setAttribute(AttributeSensor, T)}, except does not publish an attribute-change event.
     */
    <T> T setAttributeWithoutPublishing(AttributeSensor<T> sensor, T val);

    EntityConfigMap getConfigMap();

    /**
     * @return a read-only copy of all the config key/value pairs on this entity.
     */
    @Beta
    Map<ConfigKey<?>,Object> getAllConfig();

    @Beta
    public Map<AttributeSensor, Object> getAllAttributes();

    @Beta
    public void refreshInheritedConfig();

    /**
     * Must be called before the entity is started.
     * 
     * @return this entity (i.e. itself)
     */
    @Beta // for internal use only
    EntityInternal configure(Map flags);

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
     * @return Routings for accessing and inspecting the management context of the entity
     */
    EntityManagementSupport getManagementSupport();

    /**
     * Should be invoked at end-of-life to clean up the item.
     */
    @Beta
    void destroy();
    
    /** 
     * Returns the management context for the entity. If the entity is not yet managed, some 
     * operations on the management context will fail. 
     * 
     * Do not cache this object; instead call getManagementContext() each time you need to use it.
     */
    ManagementContext getManagementContext();

    /** 
     * Returns the task execution context for the entity. If the entity is not yet managed, some 
     * operations on the management context will fail.
     * 
     * Do not cache this object; instead call getExecutionContext() each time you need to use it.
     */    
    ExecutionContext getExecutionContext();
    
    SubscriptionContext getSubscriptionContext();
}
