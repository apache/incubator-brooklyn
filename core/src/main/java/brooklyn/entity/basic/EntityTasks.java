package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.util.task.DynamicTasks;

/** 
 * Contains tasks commonly performed by entities.
 * <p>
 * These tasks should be run from inside a {@link DynamicTasks} context, e.g. an {@link EffectorBody#main}. */
public class EntityTasks {

    public static <T> Task<T> effector(Entity entity, Effector<T> effector, @SuppressWarnings("rawtypes") Map parameters) {
        return DynamicTasks.addTask(Effectors.invocation(entity, effector, parameters));
    }
    
}
