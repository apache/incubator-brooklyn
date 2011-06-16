package brooklyn.event;

import brooklyn.entity.Entity;

/**
 * 
 * @author adk
 */
public interface Event<T> {
    /**
     * 
     */
    public Entity getSource();
 
    /**
     * 
     */
    public Sensor<T> getSensor();
 
    /**
     * 
     */
    public T getValue();
}