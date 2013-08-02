package brooklyn.util.task;

import brooklyn.management.Task;

/** set of convenience methods when the caller is in a task and wishes to submit additional tasks */
public class ContextTasks {

    /** adds a task to be executed as part of the current task context;
     * if the task is a DynamicSequentialTask it is added as part of its child sequence
     * and will run when that sequence runs;
     * for any other type of task, the submitted task is submitted as a DST associated (via tag) with the current task */
    public static <T> Task<T> add(Task<T> task) {
        Task<?> current = Tasks.current();
        if (!(current instanceof DynamicSequentialTask)) {
            throw new IllegalStateException("Can only dynamically add a task when inside certain suitable tasks " +
            		"(trying to add "+task+" to "+current+")");
        }
        ((DynamicSequentialTask<?>)current).addTask(task);
        return task;
    }
    
}
