package brooklyn.entity.trait;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EffectorWithExplicitImplementation;
import brooklyn.location.Location;

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

    /**
     * TODO documentation
     */
    void start(Collection<? extends Location> loc);
}
