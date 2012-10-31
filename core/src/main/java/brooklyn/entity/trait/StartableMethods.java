package brooklyn.entity.trait;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class StartableMethods {

    public static final Logger log = LoggerFactory.getLogger(StartableMethods.class);
    	
	private StartableMethods() {}

    /** Common implementation for start in parent nodes; just invokes start on all children of the entity */
	public static void start(EntityLocal e, Collection<? extends Location> locations) {
        log.info("Starting entity "+e+" at "+locations);
        Iterable<Entity> startables = Iterables.filter(e.getOwnedChildren(), Predicates.instanceOf(Startable.class));

        if (!Iterables.isEmpty(startables)) {
	        Entities.invokeEffectorList(e, startables, Startable.START, MutableMap.of("locations", locations)).getUnchecked();
        }
	}

    /** Common implementation for stop in parent nodes; just invokes stop on all children of the entity */
	public static void stop(EntityLocal e) {
        log.debug("Stopping entity "+e);
        Iterable<Entity> startables = Iterables.filter(e.getOwnedChildren(), Predicates.instanceOf(Startable.class));
		
		if (!Iterables.isEmpty(startables)) {
			Entities.invokeEffectorList(e, startables, Startable.STOP).getUnchecked();
		}
        if (log.isDebugEnabled()) log.debug("Stopped entity "+e);
	}

    /** Common implementation for restart in parent nodes; just invokes stop on all children of the entity */
    public static void restart(EntityLocal e) {
        log.debug("Restarting entity "+e);
        Iterable<Entity> startables = Iterables.filter(e.getOwnedChildren(), Predicates.instanceOf(Startable.class));
        
        if (!Iterables.isEmpty(startables)) {
            Entities.invokeEffectorList(e, startables, Startable.RESTART).getUnchecked();
        }
        if (log.isDebugEnabled()) log.debug("Restarted entity "+e);
    }
}
