package brooklyn.entity.trait;

import java.util.Collection;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;

/**
 * This interface describes an {@link brooklyn.entity.Entity} that can be started and stopped.
 *
 * The {@link Effector}s are {@link #START}, {@link #STOP} and {@link #RESTART}. The start effector takes
 * a collection of {@link Location} objects as an argument which will cause the entity to be started or stopped in all
 * these locations. The other effectors will stop or restart the entity in the location(s) it is already running in.
 */
public interface Startable {

    AttributeSensor<Boolean> SERVICE_UP = Attributes.SERVICE_UP;

    MethodEffector<Void> START = new MethodEffector<Void>(Startable.class, "start");
    MethodEffector<Void> STOP = new MethodEffector<Void>(Startable.class, "stop");
    MethodEffector<Void> RESTART = new MethodEffector<Void>(Startable.class,"restart");

    /**
     * Start the entity in the given collection of locations.
     */
    @Effector(description="Start the process/service represented by an entity")
    void start(@EffectorParam(name="locations") Collection<? extends Location> locations);

    /**
     * Stop the entity.
     */
    @Effector(description="Stop the process/service represented by an entity")
    void stop();

    /**
     * Restart the entity.
     */
    @Effector(description="Restart the process/service represented by an entity")
    void restart();
}
