package brooklyn.entity.trait;

import java.util.Collection

import brooklyn.entity.Effector
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location

/**
 * This interface describes an {@link Entity} that can be started and stopped.
 * 
 * The {@link Effector}s are {@link #START}, {@link #STOP} and {@link #RESTART}. The start effector takes
 * a collection of {@link Location} objects as an argument which will cause the entity to be started or stopped in all
 * these locations. The other effectors will stop or restart the entity in the location(s) it is already running in.
 */
public interface Startable {
//    Sensor<Boolean> SERVICE_UP = new BasicAttributeSensor<Boolean>(Boolean.class, "service.hasStarted", "Service started");
//
////    @SuppressWarnings({ "rawtypes" })
//    Effector<Void> START = new MethodEffector<Void>(Startable.&start);
////	new EffectorWithExplicitImplementation<Startable, Void>("start", Void.TYPE, 
////            Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("locations", Collection.class, "Locations to start entity in", Collections.emptyList())),
////            "Start an entity") {
////        /** serialVersionUID */
////        private static final long serialVersionUID = 6316740447259603273L;
////        @SuppressWarnings("unchecked")
////        public Void invokeEffector(Startable entity, Map m) {
////            entity.start((Collection<Location>) m.get("locations"));
////            return null;
////        }
////    };
//    Effector<Void> STOP = new MethodEffector<Void>(Startable.&stop); 
//    		//new EffectorInferredFromAnnotatedMethod<Void>(Startable.class, "stop", "Stop an entity");
//    Effector<Void> RESTART = new MethodEffector<Void>(Startable.&restart);
//			//new EffectorInferredFromAnnotatedMethod<Void>(Startable.class, "restart", "Restart an entity");
//
//    /**
//     * Start the entity in the given collection of locations.
//     */
//    @Description("Stop the process/service represented by an entity")
//    void start(@NamedParameter("locations") @DefaultValue("") Collection<? extends Location> locations);
//
//    /**
//     * Stop the entity.
//     */
//    @Description("Stop the process/service represented by an entity")
//    void stop();
//
//    /**
//     * Restart the entity.
//     */
//    @Description("Restart the process/service represented by an entity")
//    void restart();

	//FIXME prefer generics above, but am getting inconsistent class refs Startable.1 refers to <T>
	//claimed fixed with groovy 1.8.3 but eclipse compiler still on 1.8.2 (nov 2011)
	 	
	AttributeSensor SERVICE_UP = new BasicAttributeSensor(Boolean.class, "service.isUp", "Service has been started successfully and is running");

	Effector START = new MethodEffector(Startable.&start);
	Effector STOP = new MethodEffector(Startable.&stop);
	Effector RESTART = new MethodEffector(Startable.&restart);

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
