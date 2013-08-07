package brooklyn.util.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.CanAddTask;
import brooklyn.management.Task;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

@Beta // introduced in 0.6.0
public class DynamicTasks {

    private static final Logger log = LoggerFactory.getLogger(DynamicTasks.class);
    private static final ThreadLocal<CanAddTask> taskAdder = new ThreadLocal<CanAddTask>();
    
    public static void setTaskAdditionContext(CanAddTask newTaskAdder) {
        taskAdder.set(newTaskAdder);
    }
    
    public static CanAddTask getTaskAdditionContext() {
        return taskAdder.get();
    }
    
    public static void removeTaskAdditionContext() {
        taskAdder.remove();
    }

    /** tries to add the given task in the given addition context,
     * returns true if it could, false if it could not (doesn't throw anything) */
    public static boolean tryAddTask(CanAddTask adder, Task<?> task) {
        if (task==null || task.isSubmitted())
            return false;
        try {
            adder.addTask(task);
            return true;
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Could not add task "+task+" at "+adder+": "+e);
            return false;
        }        
    }
    
    /** tries to add the task to the current addition context if there is one, otherwise does nothing */
    public static <T> Task<T> autoAddTask(Task<T> task) {
        CanAddTask adder = getTaskAdditionContext();
        if (adder!=null)
            tryAddTask(adder, task);
        return task;
    }
    
    /** adds the given task to the nearest task addition context,
     * either set as a thread-local, or in the current task, or the submitter of the task, etc
     * <p>
     * throws if it cannot add */
    public static <T> Task<T> addTask(Task<T> task) {
        Preconditions.checkNotNull(task, "Task to add cannot be null");
        
        CanAddTask adder = getTaskAdditionContext();
        if (adder!=null) 
            if (tryAddTask(adder, task)) return task;
        
        Task<?> t = Tasks.current();
        while (t!=null) {
            if (t instanceof CanAddTask)
                if (tryAddTask((CanAddTask)t, task)) return task;
            t = t.getSubmittedByTask();
        }
        
        if (task.isSubmitted()) throw new IllegalStateException("Task "+task+" is already submitted; cannot add elsewhere");
        
        throw new IllegalStateException("No task addition context available for adding task "+task);
    }

}
