package brooklyn.entity.basic;

import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.trait.Startable;

/**
 * The {@link EntityDriver} for a {@link SoftwareProcess}.
 *
 * <p/>
 * In many cases it is cleaner to store entity lifecycle effectors (and sometimes other implementations) in a class to
 * which the entity delegates.  Classes implementing this interface provide this delegate, often inheriting utilities
 * specific to a particular transport (e.g. ssh) shared among many different entities.
 * <p/>
 * In this way, it is also possible for entities to cleanly support multiple mechanisms for start/stop and other methods.
 */
public interface SoftwareProcessDriver extends EntityDriver {

    /**
     * The entity whose components we are controlling.
     */
    EntityLocal getEntity();

    /**
     * Whether the entity components have started.
     */
    boolean isRunning();

    /**
     * Rebinds the driver to a pre-existing software process.
     */
    void rebind();

    /**
     * Queues tasks for starting
     */
    void start();

    /**
     * Queues tasks for restart
     * @see Startable#restart()
     * Unlike the other methods, the driver here is responsible for queueing a task to update the SERVICE_STATE sensor.
     */
    void restart();
    
    /**
     * @see Startable#stop()
     */
    void stop();
    
    /**
     * Kills the process, ungracefully and immediately where possible (e.g. with `kill -9`).
     */
    void kill();
}
