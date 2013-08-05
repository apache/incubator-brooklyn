package brooklyn.management;

/** 
 * Interface marks tasks which have explicit children,
 * typically where the task defines the ordering of running those children tasks
 * <p>
 * The {@link Task#getSubmittedByTask()} on the child will typically return the parent,
 * but note there are other means of submitting tasks (e.g. background, in the same {@link ExecutionContext}),
 * where the submitter has no API reference to the submitted tasks.
 * <p>
 * In general the children mechanism is preferred as it is easier to navigate
 * (otherwise you have to scan the {@link ExecutionContext} to find tasks submitted by a task).  
 */
public interface HasTaskChildren {

    @SuppressWarnings("rawtypes")
    public Iterable<Task> getChildrenTasks();
    
}
