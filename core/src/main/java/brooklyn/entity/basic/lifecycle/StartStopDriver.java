package brooklyn.entity.basic.lifecycle;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;

/**
 * Delegate for {@link Entity} lifecycle control.
 *
 * In many cases it is cleaner to store entity lifecycle effectors (and sometimes other implementations) in a class to 
 * which the entity delegates.  Classes implementing this interface provide this delegate, often inheriting utilities
 * specific to a particular transport (e.g. ssh) shared among many different entities.
 * <p>
 * In this way, it is also possible for entities to cleanly support multiple mechanisms for start/stop and other methods. 
 */
//TODO: Should this be renamed to 'Driver' or 'EntityDriver' ?
public interface StartStopDriver {

    /** The entity whose components we are controlling. */
    EntityLocal getEntity();

    /** The location the entity is running in. */
    Location getLocation();

    /** Whether the entity components have started. */
    boolean isRunning();
	
	/** @see Startable#start(Collection) */
	void start();

	/** @see Startable#stop() */
	void stop();

	/** @see Startable#restart() */
	void restart();
}
