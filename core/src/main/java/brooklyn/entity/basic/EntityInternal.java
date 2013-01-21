package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
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
    public void refreshInheritedConfig();

    /**
     * Must be called before the entity is started.
     * 
     * @return this entity (i.e. itself)
     */
    @Beta // for internal use only
    EntityInternal configure(Map flags);

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
     * @return The management context for the entity, or null if it is not yet managed.
     * @deprecated since 0.5 access via getManagementSupport
     */
    ManagementContext getManagementContext();

    /** 
     * @return The task execution context for the entity, or null if it is not yet managed.
     * @deprecated since 0.5 access via getManagementSupport
     */    
    ExecutionContext getExecutionContext();
}
