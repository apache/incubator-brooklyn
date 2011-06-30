package brooklyn.util.task;

import brooklyn.management.Task

/** runs tasks in parallel, no guarantees of order of starting these;
 * return value here is a collection of the return value of supplied tasks, in that order */ 
public class ParallelTask extends CompoundTask {
    
    public ParallelTask(Object... tasks) { super(tasks) }
    public ParallelTask(Collection<Object> tasks) { super(tasks) }

    protected Object runJobs() {
        List<Object> result = []
        children.each { task ->    em.submit(task) }
        children.each { task ->    result.add(task.get()) }
        return result
    }    
}
