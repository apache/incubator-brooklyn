package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import brooklyn.event.Event;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
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
     * Mutable attributes on this entity.
     * 
     * This can include activity information and status information (e.g. AttributeSensors), as well as
     * arbitrary internal properties which can make life much easier/dynamic (though we lose something in type safety)
     * e.g. jmxHost / jmxPort are handled as attributes
     */
    Map<String,Object> getAttributes();

    /**
     * The "owner" of this entity, if null if no owner. The owner is normally the entity 
     * responsible for creating/destroying this entity.
     */
    Group getOwner();
    
    /**
     * The groups that this entity is a member of. Groupings can be used to allow easy 
     * management/monitoring of a group of entities.
     */
    Collection<Group> getGroups();
    
    /**
     * Update the {@link Sensor} data for the given attribute with a new value.
     */
    <T> void updateAttribute(Sensor<T> attribute, T val);
    
    /**
     * Get the latest value of a {@link Sensor} attribute.
     */
    <T> T getAttribute(Sensor<T> attribute);

    /**
     * Return the {@link Collection} of {@link Group}s this entity belongs to.
     */
    Collection<Group> getParents();
    
    /**
     * Add this entity to a {@link Group} as a child of the parent entity.
     */
    void addParent(Group parent);
    
    /**
     * Return all the {@link Location}s this entity is deployed to.
     */
    Collection<Location> getLocations();
    
    /**
     * Allow an interested entity to subscribe to data from a named {@link Sensor} on this entity.
     * 
     * This method should forward the request to the relevant manager, but allows the entity to keep track of sensor data
     * that it must raise events for, thus minimising potential network traffic and congestion.
     * 
     * @return a subscription id which can be used to unsubscribe
     */
    <T> long subscribe(String interestedId, String sensorName, EventListener<T> listener);
        
    /**
     * The entity should raise the supplied {@link Event} and sent it to all interested parties.
     */
    <T> void raiseEvent(Event<T> event);
}
