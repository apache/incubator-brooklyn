package brooklyn.entity.group;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.trait.Startable;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class QuarantineGroupImpl extends AbstractGroupImpl implements QuarantineGroup {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class);

    @Override
    public void expungeMembers(boolean stopFirst) {
        Set<Entity> members = ImmutableSet.copyOf(getMembers());
        if (stopFirst) {
            Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
            for (Entity member : members) {
                if (member instanceof Startable) {
                    Task<Void> task = Effectors.invocation(member, Startable.STOP, ImmutableMap.of()).asTask();
                    tasks.put(member, task);
                }
            }
            DynamicTasks.queueIfPossible(Tasks.parallel("stopping "+tasks.size()+" member"+Strings.s(tasks.size())+" (parallel)", tasks.values())).orSubmitAsync(this);
            waitForTasksOnExpungeMembers(tasks);
        }
        for (Entity member : members) {
            Entities.unmanage(member);
        }
    }
    
    // TODO Quite like DynamicClusterImpl.waitForTasksOnEntityStart
    protected Map<Entity, Throwable> waitForTasksOnExpungeMembers(Map<? extends Entity,? extends Task<?>> tasks) {
        // TODO Could have CompoundException, rather than propagating first
        Map<Entity, Throwable> errors = Maps.newLinkedHashMap();

        for (Map.Entry<? extends Entity,? extends Task<?>> entry : tasks.entrySet()) {
            Entity member = entry.getKey();
            Task<?> task = entry.getValue();
            try {
                task.get();
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            } catch (Throwable t) {
                Throwable interesting = Exceptions.getFirstInteresting(t);
                LOG.error("Quarantine group "+this+" failed to stop quarantined entity "+member+" (removing): "+interesting, interesting);
                LOG.debug("Trace for: Quarantine group "+this+" failed to stop quarantined entity "+member+" (removing): "+t, t);
                // previously we unwrapped but now there is no need I think
                errors.put(member, t);
            }
        }
        return errors;
    }
}
