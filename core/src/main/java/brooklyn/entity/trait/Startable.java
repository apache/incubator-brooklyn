package brooklyn.entity.trait;

import java.util.Map;

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
	void start(Map<?,?> properties);
}
