package brooklyn.entity;

import brooklyn.management.ManagementContext;


/**
 * An application is the root of the entity hierarchy. In the parent-child relationship, it is
 * the top-level entity under which the application's entities are all places.
 * 
 * The recommended ways to write a new application are to either extend {@link brooklyn.entity.basic.ApplicationBuilder} 
 * or to extend {@link brooklyn.entity.basic.AbstractApplication}.
 */
public interface Application extends Entity {
    
    ManagementContext getManagementContext();
}
