package brooklyn.entity.trait;

import java.util.Collection;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;

/**
 * This interface describes an {@link Entity} that can be started and stopped.
 * 
 * The two {@link Effector}s available are {@link #START}, {@link #STOP} and {@link #RESTART}. The start effector takes
 * a collection of {@link Location} objects as an argument which will cause the entity to be started or stopped in all
 * these locations. The other effectors will stop or restart the entity in the location(s) it is already running in.
 */
public interface Startable {
    Sensor<Boolean> SERVICE_UP = new BasicAttributeSensor<Boolean>(Boolean.class, "service.hasStarted", "Service started");

    Effector<Void> START = new EffectorInferredFromAnnotatedMethod<Void>(Startable.class, "start", "Start an entity");
    Effector<Void> STOP = new EffectorInferredFromAnnotatedMethod<Void>(Startable.class, "stop", "Stop an entity");
    Effector<Void> RESTART = new EffectorInferredFromAnnotatedMethod<Void>(Startable.class, "restart", "Restart an entity");

    /**
     * Start the entity in the given collection of locations.
     */
    void start(@NamedParameter("locations")
	           @Description("Locations to start entity in")
	           Collection<? extends Location> locations);

    /**
     * Stop the entity.
     */
    void stop();

    /**
     * Restart the entity.
     */
    void restart();
}
