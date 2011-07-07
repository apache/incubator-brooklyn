package brooklyn.entity.trait;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EffectorWithExplicitImplementation;
import brooklyn.location.Location;

/**
 * This interface describes an {@link Entity} that can be started and stopped.
 * 
 * The two {@link Effector}s available are {@link #START} and {@link #STOP}, which both take a collection of
 * {@link Location} objects. These will cause the entity to be started or stopped in all these locations.
 */
@SuppressWarnings({ "rawtypes", "unchecked", "serial" })
public interface Startable {
    Effector<Void> START = new EffectorWithExplicitImplementation<Startable,Void>("start", Void.TYPE, Arrays.<ParameterType<?>>asList(new ParameterType<Collection<?>>() {
        public String getName() { return "locations"; }
        public Class getParameterClass() { return Collection.class; }
        public String getParameterClassName() { return null; }
        public String getDescription() { return "Collection of Locations"; } }), "Start an entity") {
        public Void invokeEffector(Startable s, Map params) {
              s.start((Collection) params.get("locations"));
              return null;
        }
    };
    Effector<Void> STOP = new EffectorWithExplicitImplementation<Startable,Void>("stop", Void.TYPE, Collections.<ParameterType<?>>emptyList(), "Stop an entity") {
        public Void invokeEffector(Startable s, Map params) {
              s.stop();
              return null;
        }
    };

    /**
     * TODO documentation
     */
    void start(Collection<? extends Location> loc);

    /**
     * TODO documentation
     */
    void stop();
}
