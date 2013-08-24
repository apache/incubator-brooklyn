package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.management.Task;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class BasicStartableImpl extends AbstractEntity implements BasicStartable {

    private static final Logger log = LoggerFactory.getLogger(BasicStartableImpl.class);
    
    public BasicStartableImpl() {
        super();
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        log.info("Starting entity "+this+" at "+locations);
        
        // essentially does StartableMethods.start(this, locations),
        // but optionally filters locations for each child
        
        LocationsFilter filter = getConfig(LOCATIONS_FILTER);
        Iterable<Entity> startables = filterStartableManagedEntities(getChildren());
        if (startables == null || Iterables.isEmpty(startables)) return;

        List<Task<?>> tasks = Lists.newArrayList();
        for (final Entity entity : startables) {
            Collection<? extends Location> l2 = locations;
            if (filter!=null) {
                l2 = filter.filterForContext(new ArrayList<Location>(locations), entity);
                log.debug("Child "+entity+" of "+this+" being started in filtered location list: "+l2);
            }
            tasks.add( Entities.invokeEffectorWithArgs(this, entity, Startable.START, l2) );
        }
        for (Task<?> t: tasks) t.getUnchecked();
    }

    @Override
    public void stop() {
        StartableMethods.stop(this);
    }

    @Override
    public void restart() {
        StartableMethods.restart(this);
    }

    // TODO make public in StartableMethods
    private static Iterable<Entity> filterStartableManagedEntities(Iterable<Entity> contenders) {
        return Iterables.filter(contenders, Predicates.and(Predicates.instanceOf(Startable.class), EntityPredicates.managed()));
    }
}
