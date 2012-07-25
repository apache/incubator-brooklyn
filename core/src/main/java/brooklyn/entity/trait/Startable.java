package brooklyn.entity.trait;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;

import java.util.Collection;

/**
 * This interface describes an {@link brooklyn.entity.Entity} that can be started and stopped.
 *
 * The {@link Effector}s are {@link #START}, {@link #STOP} and {@link #RESTART}. The start effector takes
 * a collection of {@link Location} objects as an argument which will cause the entity to be started or stopped in all
 * these locations. The other effectors will stop or restart the entity in the location(s) it is already running in.
 */
public interface Startable {

    AttributeSensor<Boolean> SERVICE_UP = new BasicAttributeSensor<Boolean>(Boolean.class, "service.isUp", "Service has been started successfully and is running");

    Effector<Void> START = new MethodEffector<Void>(Startable.class, "start");
    Effector<Void> STOP = new MethodEffector<Void>(Startable.class, "stop");
    Effector<Void> RESTART = new MethodEffector<Void>(Startable.class,"restart");

    /**
     * Start the entity in the given collection of locations.
     */
    @Description("Start the process/service represented by an entity")
    void start(@NamedParameter("locations") Collection<? extends Location> locations);

    /**
     * Stop the entity.
     */
    @Description("Stop the process/service represented by an entity")
    void stop();

    /**
     * Restart the entity.
     */
    @Description("Restart the process/service represented by an entity")
    void restart();
}
