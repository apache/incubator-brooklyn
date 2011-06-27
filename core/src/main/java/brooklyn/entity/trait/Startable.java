package brooklyn.entity.trait;

import java.util.Collection;

import brooklyn.location.Location;


public interface Startable {
//	Effector<Void> START = new InterfaceEffector(Startable.class, "start", "Start an entity");
//	Effector<Void> START = new InterfaceEffector<Startable, Void>("start", Void.TYPE, Collections.<ParameterType<?>>emptyList(), "Starts an entity") {
//        public Void call(Startable s, Map params) {
//	          return s.start(params);
//	    }
//	};

	/**
	 * TODO documentation
	 */
	void start(Collection<? extends Location> loc);
}
