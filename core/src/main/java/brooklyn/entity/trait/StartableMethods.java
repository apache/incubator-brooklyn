package brooklyn.entity.trait;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.CompoundRuntimeException;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class StartableMethods {

    public static final Logger log = LoggerFactory.getLogger(StartableMethods.class);
    	
	private StartableMethods() {}

    /** Common implementation for start in parent nodes; just invokes start on all children of the entity */
	public static void start(EntityLocal e, Collection<? extends Location> locations) {
        log.debug("Starting entity "+e+" at "+locations);
        Iterable<Entity> startables = filterStartableManagedEntities(e.getChildren());

        if (!Iterables.isEmpty(startables)) {
	        Entities.invokeEffector(e, startables, Startable.START, MutableMap.of("locations", locations)).getUnchecked();
        }
	}
	
    /** Common implementation for stop in parent nodes; just invokes stop on all children of the entity */
	public static void stop(EntityLocal e) {
        log.debug("Stopping entity "+e);
        Iterable<Entity> startables = filterStartableManagedEntities(e.getChildren());
		
		if (!Iterables.isEmpty(startables)) {
			Entities.invokeEffector(e, startables, Startable.STOP).getUnchecked();
		}
        if (log.isDebugEnabled()) log.debug("Stopped entity "+e);
	}

    /** Common implementation for restart in parent nodes; just invokes stop on all children of the entity */
    public static void restart(EntityLocal e) {
        log.debug("Restarting entity "+e);
        Iterable<Entity> startables = filterStartableManagedEntities(e.getChildren());
        
        if (!Iterables.isEmpty(startables)) {
            Entities.invokeEffector(e, startables, Startable.RESTART).getUnchecked();
        }
        if (log.isDebugEnabled()) log.debug("Restarted entity "+e);
    }
    
    private static Iterable<Entity> filterStartableManagedEntities(Iterable<Entity> contenders) {
        return Iterables.filter(contenders, Predicates.and(Predicates.instanceOf(Startable.class), EntityPredicates.managed()));
    }

    public static void stopSequentially(Iterable<? extends Startable> entities) {
        List<Exception> exceptions = Lists.newArrayList();
        List<Startable> failedEntities = Lists.newArrayList();
        
        for (Startable entity : entities) {
            try {
                entity.stop();
            } catch (Exception e) {
                log.warn("Error stopping "+entity+"; continuing with shutdown", e);
                exceptions.add(e);
                failedEntities.add(entity);
            }
        }
        
        if (exceptions.size() > 0) {
            throw new CompoundRuntimeException("Error stopping "+(failedEntities.size() > 1 ? "entities" : "entity")+": "+failedEntities, exceptions);
        }
    }
}
