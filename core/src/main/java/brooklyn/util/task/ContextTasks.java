package brooklyn.util.task;

import brooklyn.management.Task;

public class ContextTasks {

    /** adds a task to be executed as part of the current task context;
     * if the task is a DynamicSequentialTask it is added as part of its child sequence
     * and will run when that sequence runs;
     * for any other type of task, the submitted task is submitted as a DST associated (via tag) with the current task */
    public static <T> Task<T> add(Task<T> task) {
        throw new UnsupportedOperationException("not implemented yet");  // TODO
//        return task;
    }
    
}
