package brooklyn.entity.trait;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod;
import brooklyn.entity.basic.EffectorWithExplicitImplementation;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;

/**
 * This interface describes an {@link Entity} that can be started and stopped.
 * 
 * The {@link Effector}s are {@link #START}, {@link #STOP} and {@link #RESTART}. The start effector takes
 * a collection of {@link Location} objects as an argument which will cause the entity to be started or stopped in all
 * these locations. The other effectors will stop or restart the entity in the location(s) it is already running in.
 */
public interface Startable {
    Sensor<Boolean> SERVICE_UP = new BasicAttributeSensor<Boolean>(Boolean.class, "service.hasStarted", "Service started");

    @SuppressWarnings({ "rawtypes" })
    Effector<Void> START = new EffectorWithExplicitImplementation<Startable, Void>("start", Void.TYPE, 
            Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("locations", Collection.class, "Locations to start entity in", Collections.emptyList())),
            "Start an entity") {
        /** serialVersionUID */
        private static final long serialVersionUID = 6316740447259603273L;
        @SuppressWarnings("unchecked")
        public Void invokeEffector(Startable entity, Map m) {
            entity.start((Collection<Location>) m.get("locations"));
            return null;
        }
    };
    Effector<Void> STOP = new EffectorInferredFromAnnotatedMethod<Void>(Startable.class, "stop", "Stop an entity");
    Effector<Void> RESTART = new EffectorInferredFromAnnotatedMethod<Void>(Startable.class, "restart", "Restart an entity");

    /**
     * Start the entity in the given collection of locations.
     */
    void start(Collection<Location> locations);

    /**
     * Stop the entity.
     */
    void stop();

    /**
     * Restart the entity.
     */
    void restart();
}
