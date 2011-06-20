package brooklyn.entity;

import java.util.Collection;
import java.util.Map;

import brooklyn.event.Event;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.location.Location;

/**
 * The basic interface for an OverPaaS entity.
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
    
    Application getApplication();
    
    
    //FIXME should these be here?  or is Abstract good enough?
    
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
    
    <T> void updateAttribute(Sensor<T> attribute, T val);
    
    <T> void raiseEvent(Event<T> event);
    
    Collection<Location> getLocations();
}
