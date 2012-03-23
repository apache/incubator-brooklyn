package brooklyn.entity.trait;

import java.util.Collection
import java.util.List
import java.util.concurrent.ExecutionException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.location.Location
import brooklyn.management.Task

public class StartableMethods {

    public static final Logger log = LoggerFactory.getLogger(StartableMethods.class);
    	
	private StartableMethods() {}

    /** Common implementation for start in parent nodes; just invokes start on all children of the entity */
	public static void start(EntityLocal e, Collection<Location> locations) {
        log.info("Starting entity "+e+" at "+locations);
        List<Entity> startables = e.ownedChildren.findAll { it in Startable }
        if (startables && !startables.isEmpty() && locations && !locations.isEmpty()) {
	        Task start = Entities.invokeEffectorList(e, startables, Startable.START, [ locations:locations ])
	        try {
	            start.get()
	        } catch (ExecutionException ee) {
	            throw ee.cause
	        }
        }
	}

    /** Common implementation for stop in parent nodes; just invokes stop on all children of the entity */
	public static void stop(EntityLocal e) {
        log.debug("Stopping entity "+e);
		List<Entity> startables = e.ownedChildren.findAll { it in Startable }
		if (startables && !startables.isEmpty()) {
			Task task = Entities.invokeEffectorList(e, startables, Startable.STOP)
			try {
				task.get()
			} catch (ExecutionException ee) {
				throw ee.cause
			}
		}
        if (log.isDebugEnabled()) log.debug("Stopped entity "+e);
	}
	
}
