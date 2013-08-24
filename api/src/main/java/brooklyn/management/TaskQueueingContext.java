package brooklyn.management;

import java.util.List;

import com.google.common.annotations.Beta;

/**
 * Marks a place where tasks can be added, e.g. a task which allows children to be added (including after it is activated);
 * if the implementer of this is also a task, then it may be picked up by hierarchical methods (e.g. in DynamicTasks).
 * 
 * @since 0.6.0
 */
@Beta
public interface TaskQueueingContext {

    /** queues the task for submission as part of this queueing context; should mark it as submitted */
    public void queue(Task<?> t);
    
    /** returns a list of queued tasks (immutable copy) */
    public List<Task<?>> getQueue();

    /** returns the last task in the queue, or null if none */
    public Task<?> last();
    
}
