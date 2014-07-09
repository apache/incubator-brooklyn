package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.internal.EntityManagementSupport;
import brooklyn.mementos.EntityMemento;
import brooklyn.util.config.ConfigBag;

import com.google.common.annotations.Beta;

/** 
 * Extended Entity interface with additional functionality that is purely-internal (i.e. intended 
 * for the brooklyn framework only).
 */
@Beta
public interface EntityInternal extends EntityLocal, Rebindable {
    
    void addLocations(Collection<? extends Location> locations);

    void removeLocations(Collection<? extends Location> locations);

    void clearLocations();

    /**
     * 
     * Like {@link EntityLocal#setAttribute(AttributeSensor, Object)}, except does not publish an attribute-change event.
     */
    <T> T setAttributeWithoutPublishing(AttributeSensor<T> sensor, T val);

    EntityConfigMap getConfigMap();

    /**
     * @return a read-only copy of all the config key/value pairs on this entity.
     */
    @Beta
    Map<ConfigKey<?>,Object> getAllConfig();

    /**
     * Returns a read-only view of all the config key/value pairs on this entity, backed by a string-based map, 
     * including config names that did not match anything on this entity.
     */
    @Beta
    ConfigBag getAllConfigBag();

    /**
     * Returns a read-only view of the local (i.e. not inherited) config key/value pairs on this entity, 
     * backed by a string-based map, including config names that did not match anything on this entity.
     */
    @Beta
    ConfigBag getLocalConfigBag();

    @Beta
    public Map<AttributeSensor, Object> getAllAttributes();

    @Beta
    public void removeAttribute(AttributeSensor<?> attribute);
    
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
    
    /** returns the dynamic type corresponding to the type of this entity instance */
    @Beta
    EntityDynamicType getMutableEntityType();

    /** returns the effector registered against a given name */
    @Beta
    Effector<?> getEffector(String effectorName);
    
    Map<String, String> toMetadataRecord();
    
    @Override
    RebindSupport<EntityMemento> getRebindSupport();

    /**
     * Can be called to request that the entity be persisted.
     * This persistence may happen asynchronously, or may not happen at all if persistence is disabled.
     */
    void requestPersist();
}
