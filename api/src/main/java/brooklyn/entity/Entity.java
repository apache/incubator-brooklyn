package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;

import brooklyn.event.Event;
import brooklyn.event.EventListener;
import brooklyn.location.Location;

/**
 * The basic interface for a Brooklyn entity.
 * 
 * @see AbstractEntity
 */
public interface Entity extends Serializable {
    /**
     * The unique identifier for this entity.
     */
    String getId();
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();
    
    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    EntityClass getEntityClass();
    
    /*
     * Return the {@link Application} this entity is registered with.
     */
    Application getApplication();

    /**
     * Return the id of the {@link Application} this entity is registered with.
     */
    String getApplicationId();

    /**
     * Mutable attributes on this entity.
     * 
     * This can include activity information and status information (e.g. AttributeSensors), as well as
     * arbitrary internal properties which can make life much easier/dynamic (though we lose something in type safety)
     * e.g. jmxHost / jmxPort are handled as attributes
     */
    Map<String,Object> getAttributes();

    /**
     * The owner of this entity, null if no owner.
     *
     * The owner is normally the entity responsible for creating/destroying this entity.
     */
    Group getOwner();
    
    /**
     * The {@link Collection} of {@link Group}s that this entity is a member of.
     *
     * Groupings can be used to allow easy management/monitoring of a group of entities.
     */
    Collection<Group> getGroups();
    
    /**
     * Update the {@link Sensor} data for the given attribute with a new value.
     * 
     * @return the old value for the attribute
     */
    <T> T updateAttribute(Sensor<T> attribute, T val);
    
    /**
     * Get the latest value of a {@link Sensor} attribute.
     */
    <T> T getAttribute(Sensor<T> attribute);

    /**
     * Add this entity to a {@link Group} as a child of the parent entity.
     */
    void addGroup(Group parent);

    /**
     * The ids of all {@link Group}s this entity belongs to.
     */
    Collection<String> getGroupIds();

    /**
     * Return all the {@link Location}s this entity is deployed to.
     */
    Collection<Location> getLocations();
    
    /**
     * Allow us to subscribe to data from a {@link Sensor} on another entity.
     * 
     * @return a subscription id which can be used to unsubscribe
     */
    <T> long subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener);
        
    /**
     * The entity should raise the supplied {@link Event} and sent it to all interested parties.
     */
    <T> void raiseEvent(Event<T> event);
}
