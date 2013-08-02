package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DynamicSequentialTask;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

/** Typical implementations override {@link #main(ConfigBag)} to do the work of the effector
 * <p>
 *   */
public abstract class EffectorBody<T> {
    /** Does the work of the effector, either in place, or (better) by building up
     * subtasks, which can by added using {@link DynamicTasks} methods
     * (and various convenience methods which do that automatically; see subclasses of EffectorBody 
     * for more info on usage; or see {@link DynamicSequentialTask} for details of the threading model
     * by which added tasks are placed in a secondary thread)
     * <p>
     * The associated entity can be accessed through the {@link #entity()} method.
     */
    public abstract T main(ConfigBag parameters);
    
    // TODO could support an 'init' method which is done at creation,
    // as a place where implementers can describe the structure of the task before it executes
    // (and init gets invoked in EffectorBodyTaskFactory.newTask _before_ the task is submitted and main is called)

    
    
    // ---- convenience method(s) for implementers of main -- see subclasses and *Tasks statics for more!
    
    protected Entity entity() {
        return Tasks.tag(Entity.class);
    }

}
