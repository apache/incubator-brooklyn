package brooklyn.entity.trait;

import java.util.Map;

public interface Startable {
//	Effector<Void> START = new InterfaceEffector(Startable.class, "start", "Start an entity");
//	Effector<Void> START = new AbstractEffector<Void>("start", Void.TYPE, Collections.<ParameterType<?>>emptyList(), "Starts an entity") {
//        public Void call(Entity e, Map params) {
//	        if (e instanceof Startable) ((Startable) e).start(params);
//	        return null;
//	    }
//	};

	/**
	 * TODO documentation
	 */
	void start(Map<?,?> properties);
}
